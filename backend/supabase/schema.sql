-- Mozic — Supabase schema (C1, Postgres/PostgREST/Auth flavor)
-- Run this once in the Supabase dashboard: SQL Editor → New query → paste → Run.
-- Idempotent: safe to re-run (drops/recreates policies and the trigger function).
--
-- Explicit GRANTs are required throughout because "Automatically expose new
-- tables" was deliberately left OFF at project creation (RLS policies alone
-- don't grant table-level access — Postgres checks a base GRANT first, RLS
-- only narrows which rows are visible once that base access exists).

grant usage on schema public to anon, authenticated, service_role;

-- ============================================================
-- profiles — the app's User model. auth.users (Supabase Auth) is
-- still the source of truth for login (email/password) and is what
-- actually rejects a duplicate-email signup; email is mirrored here
-- too (kept in sync by the trigger below) so profiles carries its
-- own independent unique constraint as a second guard, and so the
-- app doesn't need a separate auth.users read just to know a user's
-- email. One row per auth user.
-- ============================================================
create table if not exists public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    username text unique not null,
    display_name text not null,
    avatar_url text,
    is_premium boolean not null default false
);

-- Migration for profiles created before `email` existed here — safe to
-- re-run: adds the column, backfills only rows still missing it from
-- auth.users, then enforces not-null/unique once every row is filled in.
alter table public.profiles add column if not exists email text;
update public.profiles p set email = u.email from auth.users u where p.id = u.id and p.email is null;
alter table public.profiles alter column email set not null;
create unique index if not exists profiles_email_key on public.profiles (email);

alter table public.profiles enable row level security;

drop policy if exists "profiles_select_all" on public.profiles;
create policy "profiles_select_all" on public.profiles
    for select using (true);

drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_update_own" on public.profiles
    for update using (auth.uid() = id) with check (auth.uid() = id);

grant select on public.profiles to anon, authenticated, service_role;
grant update on public.profiles to authenticated, service_role;

-- Auto-create a profile row whenever a new Supabase Auth user is created.
-- Demo/seed users are created via POST /auth/v1/admin/users with a
-- user_metadata payload (username/display_name/avatar_url/is_premium);
-- this trigger copies that metadata into the profile row it creates.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
    insert into public.profiles (id, username, display_name, avatar_url, is_premium, email)
    values (
        new.id,
        coalesce(new.raw_user_meta_data ->> 'username', split_part(new.email, '@', 1)),
        coalesce(new.raw_user_meta_data ->> 'display_name', split_part(new.email, '@', 1)),
        new.raw_user_meta_data ->> 'avatar_url',
        coalesce((new.raw_user_meta_data ->> 'is_premium')::boolean, false),
        new.email
    );
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- ============================================================
-- avatars storage — the `avatars` bucket itself is created via the
-- Storage Management API (public:true), not here; these are the
-- object-level RLS policies letting a logged-in user upload/replace
-- only their own avatar. Path convention: {user id}/avatar (no file
-- extension — the upload's Content-Type header is what an image loader
-- keys off, not the URL). Public read needs no policy: the bucket's own
-- "public" flag serves /storage/v1/object/public/... unconditionally.
--
-- storage.buckets ALSO has RLS, separate from storage.objects — an insert
-- into objects needs to read the bucket's own row first (size/mime-type
-- limits), so without this, every write 403s with a generic RLS message
-- that gives no hint it's actually the *bucket* read that's failing.
-- audio/covers never hit this: they're only ever written by the secret-key
-- seed script, which bypasses RLS entirely — avatars is the first bucket
-- written to by a real logged-in user from the app itself.
-- ============================================================
drop policy if exists "buckets_select_all" on storage.buckets;
create policy "buckets_select_all" on storage.buckets
    for select using (true);

drop policy if exists "avatars_insert_own" on storage.objects;
create policy "avatars_insert_own" on storage.objects
    for insert to authenticated
    with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "avatars_update_own" on storage.objects;
create policy "avatars_update_own" on storage.objects
    for update to authenticated
    using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text)
    with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "avatars_delete_own" on storage.objects;
create policy "avatars_delete_own" on storage.objects
    for delete to authenticated
    using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

-- ============================================================
-- songs — the catalog. Public read-only via the API; writes
-- only via the secret key (seeding/admin), matching C1's
-- "catalog is server-managed" model — except `popularity`, which
-- real playback increments through the narrow RPC below.
-- ============================================================
create table if not exists public.songs (
    id text primary key,
    title text not null,
    artist_name text not null,
    cover_image_url text not null,
    audio_url text not null,
    duration_ms bigint,
    created_at timestamptz not null default now(),
    popularity int not null default 0
);

alter table public.songs enable row level security;

drop policy if exists "songs_select_all" on public.songs;
create policy "songs_select_all" on public.songs
    for select using (true);

grant select on public.songs to anon, authenticated, service_role;
grant insert, update, delete on public.songs to service_role;

create index if not exists songs_popularity_idx on public.songs (popularity desc);
create index if not exists songs_created_at_idx on public.songs (created_at desc);
create index if not exists songs_artist_name_idx on public.songs (artist_name);

-- Atomic "+1" on a real playback transition (`Media3PlayerController`'s
-- `onMediaItemTransition`, same moment it already calls `recordPlayed()` for
-- the local Recently-played list) — never a plain PATCH, which would need a
-- read-then-write from the client and race across concurrent listens/devices.
-- `security definer` because anon/authenticated have no direct UPDATE grant
-- on `songs` (see the table comment above) — this is the one deliberate,
-- narrow exception: it can only ever add 1 to exactly one column of one row,
-- nothing else the direct table grant would also allow.
create or replace function public.increment_song_popularity(song_id text)
returns void
language sql
security definer
set search_path = public
as $$
    update public.songs set popularity = popularity + 1 where id = song_id;
$$;

grant execute on function public.increment_song_popularity(text) to anon, authenticated, service_role;

-- ============================================================
-- playlists / playlist_songs
-- ============================================================
create table if not exists public.playlists (
    id text primary key,
    title text not null,
    cover_image_url text,
    owner_id uuid references public.profiles (id) on delete cascade,
    is_public boolean not null default true,
    category text not null check (category in ('WORLD', 'LOCAL', 'USER'))
);

-- Pre-existing deployments created the column `not null` — `create table if
-- not exists` above is a no-op against them, so drop the constraint
-- explicitly. User-created playlists (the app's new "Create playlist" flow)
-- have no cover to supply at creation time.
alter table public.playlists alter column cover_image_url drop not null;

alter table public.playlists enable row level security;

drop policy if exists "playlists_select_visible" on public.playlists;
create policy "playlists_select_visible" on public.playlists
    for select using (is_public = true or owner_id = auth.uid());

-- The app's "Create playlist" flow inserts as the signed-in user, always
-- into the USER category — WORLD/LOCAL rows stay curator/service_role-only.
drop policy if exists "playlists_insert_own" on public.playlists;
create policy "playlists_insert_own" on public.playlists
    for insert with check (owner_id = auth.uid() and category = 'USER');

grant select on public.playlists to anon, authenticated, service_role;
grant insert on public.playlists to authenticated;
grant insert, update, delete on public.playlists to service_role;

create index if not exists playlists_category_idx on public.playlists (category);

create table if not exists public.playlist_songs (
    playlist_id text not null references public.playlists (id) on delete cascade,
    song_id text not null references public.songs (id) on delete cascade,
    position int not null,
    primary key (playlist_id, song_id)
);

alter table public.playlist_songs enable row level security;

drop policy if exists "playlist_songs_select_visible" on public.playlist_songs;
create policy "playlist_songs_select_visible" on public.playlist_songs
    for select using (
        exists (
            select 1 from public.playlists p
            where p.id = playlist_songs.playlist_id
              and (p.is_public = true or p.owner_id = auth.uid())
        )
    );

-- The Now Playing screen's "Add to playlist" flow inserts as the playlist's
-- own owner (never into someone else's playlist, since the client only ever
-- lists the caller's own USER-category playlists to add into).
drop policy if exists "playlist_songs_insert_owner" on public.playlist_songs;
create policy "playlist_songs_insert_owner" on public.playlist_songs
    for insert with check (
        exists (
            select 1 from public.playlists p
            where p.id = playlist_songs.playlist_id
              and p.owner_id = auth.uid()
        )
    );

grant select on public.playlist_songs to anon, authenticated, service_role;
grant insert on public.playlist_songs to authenticated;
grant insert, update, delete on public.playlist_songs to service_role;

create index if not exists playlist_songs_playlist_idx on public.playlist_songs (playlist_id, position);

-- ============================================================
-- follows — public follow graph; writes require the caller to
-- be the follower.
-- ============================================================
create table if not exists public.follows (
    follower_id uuid not null references public.profiles (id) on delete cascade,
    followee_id uuid not null references public.profiles (id) on delete cascade,
    primary key (follower_id, followee_id)
);

alter table public.follows enable row level security;

drop policy if exists "follows_select_all" on public.follows;
create policy "follows_select_all" on public.follows
    for select using (true);

drop policy if exists "follows_insert_own" on public.follows;
create policy "follows_insert_own" on public.follows
    for insert with check (follower_id = auth.uid());

drop policy if exists "follows_delete_own" on public.follows;
create policy "follows_delete_own" on public.follows
    for delete using (follower_id = auth.uid());

-- The Android client's follow() upsert (`Prefer: resolution=merge-duplicates`)
-- issues an `INSERT ... ON CONFLICT DO UPDATE`, so both an UPDATE grant and an
-- UPDATE RLS policy are required even though there's no app-level "edit a
-- follow" feature — omitting either 403s every re-follow of an
-- already-followed user with "permission denied for table follows" (42501).
drop policy if exists "follows_update_own" on public.follows;
create policy "follows_update_own" on public.follows
    for update using (follower_id = auth.uid()) with check (follower_id = auth.uid());

grant select on public.follows to anon, authenticated, service_role;
grant insert, update, delete on public.follows to authenticated, service_role;

create index if not exists follows_follower_idx on public.follows (follower_id);
create index if not exists follows_followee_idx on public.follows (followee_id);

-- ============================================================
-- conversations / messages — DM chat. Read-only to participants
-- via the API; C3/C5's WebSocket service writes with the secret
-- key (status transitions, server-side receipt logic), so no
-- client UPDATE policy on messages yet.
-- ============================================================
create table if not exists public.conversations (
    id text primary key,
    user_a uuid not null references public.profiles (id) on delete cascade,
    user_b uuid not null references public.profiles (id) on delete cascade,
    unique (user_a, user_b)
);

alter table public.conversations enable row level security;

drop policy if exists "conversations_select_participant" on public.conversations;
create policy "conversations_select_participant" on public.conversations
    for select using (auth.uid() = user_a or auth.uid() = user_b);

drop policy if exists "conversations_insert_participant" on public.conversations;
create policy "conversations_insert_participant" on public.conversations
    for insert with check (auth.uid() = user_a or auth.uid() = user_b);

grant select, insert on public.conversations to authenticated, service_role;

create table if not exists public.messages (
    id uuid primary key default gen_random_uuid(),
    conversation_id text not null references public.conversations (id) on delete cascade,
    sender_id uuid not null references public.profiles (id),
    sent_at timestamptz not null default now(),
    status text not null default 'SENT' check (status in ('SENDING', 'SENT', 'READ')),
    payload_type text not null check (payload_type in ('text', 'song')),
    text text,
    song_id text references public.songs (id),
    song_title text,
    song_artist text,
    song_cover text
);

alter table public.messages enable row level security;

drop policy if exists "messages_select_participant" on public.messages;
create policy "messages_select_participant" on public.messages
    for select using (
        exists (
            select 1 from public.conversations c
            where c.id = messages.conversation_id
              and auth.uid() in (c.user_a, c.user_b)
        )
    );

drop policy if exists "messages_insert_participant" on public.messages;
create policy "messages_insert_participant" on public.messages
    for insert with check (
        sender_id = auth.uid()
        and exists (
            select 1 from public.conversations c
            where c.id = messages.conversation_id
              and auth.uid() in (c.user_a, c.user_b)
        )
    );

grant select, insert on public.messages to authenticated, service_role;
grant update on public.messages to service_role;

create index if not exists messages_conversation_idx on public.messages (conversation_id, sent_at desc);

-- ============================================================
-- search_catalog — cross-table search RPC. PostgREST exposes
-- this as POST /rest/v1/rpc/search_catalog. One discriminated
-- row per hit (type + one populated jsonb column) mirrors the
-- domain's sealed SearchResult shape.
-- ============================================================
create or replace function public.search_catalog(q text, result_type text default 'all')
returns table (
    type text,
    song jsonb,
    artist jsonb,
    playlist jsonb
)
language sql
stable
as $$
    select 'song', to_jsonb(s), null::jsonb, null::jsonb
    from public.songs s
    where (result_type = 'song' or result_type = 'all')
      and (s.title ilike '%' || q || '%' or s.artist_name ilike '%' || q || '%')

    union all

    select 'artist', null::jsonb,
        jsonb_build_object(
            'id', lower(regexp_replace(a.artist_name, '[^a-zA-Z0-9]+', '-', 'g')),
            'name', a.artist_name,
            'imageUrl', a.cover_image_url
        ), null::jsonb
    from (
        select distinct on (artist_name) artist_name, cover_image_url
        from public.songs
        where artist_name ilike '%' || q || '%'
        order by artist_name, popularity desc
    ) a
    where (result_type = 'artist' or result_type = 'all')

    union all

    select 'playlist', null::jsonb, null::jsonb, to_jsonb(p)
    from public.playlists p
    where (result_type = 'playlist' or result_type = 'all')
      and p.is_public = true
      and p.title ilike '%' || q || '%';
$$;

grant execute on function public.search_catalog(text, text) to anon, authenticated, service_role;

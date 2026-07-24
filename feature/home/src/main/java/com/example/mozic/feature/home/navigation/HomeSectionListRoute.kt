package com.example.mozic.feature.home.navigation

import kotlinx.serialization.Serializable

/**
 * [sectionName] is a plain `HomeSection.name` — avoids needing
 * `:core:domain`'s enum to be its own `@Serializable` nav type.
 */
@Serializable
data class HomeSectionListRoute(val sectionName: String)

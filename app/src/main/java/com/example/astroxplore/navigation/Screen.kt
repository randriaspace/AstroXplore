package com.example.astroxplore.navigation

import kotlinx.serialization.Serializable

@Serializable
public sealed interface Screen {
    @Serializable
    data object AuthGraph : Screen

    @Serializable
    data object OnboardingGraph : Screen

    @Serializable
    data object MainGraph : Screen

    @Serializable
    data object Feed : Screen

    @Serializable
    data object Groups : Screen

    @Serializable
    data class Explore(val autofocus: Boolean = false) : Screen

    @Serializable
    data object Library : Screen

    @Serializable
    data object Profile : Screen

    @Serializable
    data object Onboarding : Screen

    @Serializable
    data object Login : Screen

    @Serializable
    data object Signup : Screen

    @Serializable
    data object Interests : Screen

    @Serializable
    data object EditProfile : Screen

    @Serializable
    data class PaperDetails(val bibcode: String) : Screen

    @Serializable
    data class GroupDetails(val groupId: String) : Screen
}
package com.app.pustakam.feature.auth.domain.profile

import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.profile.PublicUser
import com.app.pustakam.core.model.models.profile.UpdateProfileReq
import com.app.pustakam.core.model.models.profile.UsernameAvailability
import com.app.pustakam.core.model.models.response.User
import com.app.pustakam.core.model.validation.UsernameRules
import com.app.pustakam.core.network.MediaUpload
import com.app.pustakam.feature.auth.domain.repository.PEOPLE_PAGE_SIZE
import com.app.pustakam.feature.auth.domain.usecase.AuthBaseUseCase
import kotlinx.coroutines.flow.Flow

/**
 * 👤 One class, one action — the same shape the notes and chat use cases follow.
 *
 * These live under `domain/profile/` inside :feature:auth rather than in a :feature:profile module.
 * Feature modules may not depend on each other, and :feature:chat needs usernames and avatars —
 * but it needs the TYPES, which are in :core:model, not this logic. Namespacing them here keeps a
 * later extraction mechanical. See ai/dependency-graph.md.
 */
class GetMyProfileUseCase : AuthBaseUseCase() {
    suspend operator fun invoke(): Flow<Result<BaseResponse<User>, Error>> =
        getBaseApiCall { authRepository.getUser("") }
}

class UpdateProfileUseCase : AuthBaseUseCase() {
    suspend operator fun invoke(request: UpdateProfileReq): Flow<Result<BaseResponse<User>, Error>> =
        getBaseApiCall { authRepository.updateProfile(request) }
}

class UploadAvatarUseCase : AuthBaseUseCase() {
    suspend operator fun invoke(file: MediaUpload): Flow<Result<BaseResponse<User>, Error>> =
        getBaseApiCall { authRepository.uploadAvatar(file) }
}

class SetUsernameUseCase : AuthBaseUseCase() {
    suspend operator fun invoke(username: String): Flow<Result<BaseResponse<User>, Error>> =
        getBaseApiCall { authRepository.setUsername(username) }
}

/**
 * 🆔 The shape check runs locally first, so a handle that cannot possibly be valid never costs a
 * round trip — and so the composer can explain why without waiting for the network.
 */
class CheckUsernameUseCase : AuthBaseUseCase() {
    fun localRejection(username: String) = UsernameRules.rejectionFor(username)

    fun isWorthChecking(username: String) = UsernameRules.isWorthChecking(username)

    suspend operator fun invoke(username: String): Flow<Result<BaseResponse<UsernameAvailability>, Error>> =
        getBaseApiCall { authRepository.checkUsername(username) }
}

class GetPublicProfileUseCase : AuthBaseUseCase() {
    suspend operator fun invoke(username: String): Flow<Result<BaseResponse<PublicUser>, Error>> =
        getBaseApiCall { authRepository.getPublicProfile(username) }
}

class SearchPeopleUseCase : AuthBaseUseCase() {
    suspend operator fun invoke(
        query: String,
        limit: Int = PEOPLE_PAGE_SIZE,
    ): Flow<Result<BaseResponse<List<PublicUser>>, Error>> =
        getBaseApiCall { authRepository.searchPeople(query, limit) }
}

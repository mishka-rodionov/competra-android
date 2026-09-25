package com.competra.profile.presentation.profile_editor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.competra.analytics.AnalyticsEvent
import com.competra.analytics.AnalyticsTracker
import com.competra.domain.exception.NetworkException
import com.competra.domain.models.CropRect
import com.competra.domain.models.Gender
import com.competra.domain.models.NetworkErrorEvent
import com.competra.domain.models.user.User
import com.competra.domain.repository.LoadingRepository
import com.competra.domain.repository.NetworkErrorRepository
import com.competra.domain.repository.UploadRepository
import com.competra.domain.repository.user.UserProfileRepository
import com.competra.domain.repository.user.UserRepository
import com.competra.ui.BaseAction
import com.competra.ui.viewmodel.BaseViewModel
import com.competra.utils.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ViewModel для экрана редактирования профиля.
 * Управляет загрузкой, редактированием и сохранением данных пользователя.
 *
 * @param userRepository Репозиторий для работы с данными пользователя.
 * @param networkErrorRepository Репозиторий для передачи сетевых ошибок в MainActivity.
 */
class ProfileEditorViewModel(
    private val userRepository: UserRepository,
    private val userProfileRepository: UserProfileRepository,
    private val networkErrorRepository: NetworkErrorRepository,
    private val uploadRepository: UploadRepository,
    private val context: Context,
    private val loadingRepository: LoadingRepository,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<ProfileEditorState>(ProfileEditorState()) {

    init {
        loadUser()
    }

    override fun onAction(action: BaseAction) {
        when (action) {
            is ProfileEditorAction.UpdateFirstName -> updateFirstName(action.firstName)
            is ProfileEditorAction.UpdateLastName -> updateLastName(action.lastName)
            is ProfileEditorAction.UpdateMiddleName -> updateMiddleName(action.middleName)
            is ProfileEditorAction.UpdatePhoneNumber -> updatePhoneNumber(action.phoneNumber)
            is ProfileEditorAction.UpdateEmail -> updateEmail(action.email)
            is ProfileEditorAction.UpdateGender -> updateGender(action.gender)
            is ProfileEditorAction.SaveProfile -> saveProfile()
            is ProfileEditorAction.PhotoPicked -> updateState { copy(pendingCropUri = action.uri) }
            is ProfileEditorAction.CancelCrop -> updateState { copy(pendingCropUri = null) }
            is ProfileEditorAction.ConfirmCrop -> confirmCrop(action.cropRect)
        }
    }

    /**
     * Загружает информацию о текущем пользователе.
     */
    private fun loadUser() {
        updateState { copy(isLoading = true) }
        viewModelScope.launch {
            loadingRepository.emit(true)
            userRepository.retrieveUser()
                .onSuccess { user ->
                    updateState { copy(user = user, isLoading = false) }
                }
                .onFailure {
                    updateState { copy(isLoading = false, error = "Ошибка загрузки данных") }
                    handleFailure(it)
                }
            loadingRepository.emit(false)
        }
    }

    private fun updateFirstName(firstName: String) {
        updateState { copy(user = user?.copy(firstName = firstName)) }
    }

    private fun updateLastName(lastName: String) {
        updateState { copy(user = user?.copy(lastName = lastName)) }
    }

    private fun updateMiddleName(middleName: String) {
        updateState { copy(user = user?.copy(middleName = middleName)) }
    }

    private fun updatePhoneNumber(phoneNumber: String) {
        updateState { copy(user = user?.copy(phoneNumber = phoneNumber)) }
    }

    private fun updateEmail(email: String) {
        updateState { copy(user = user?.copy(email = email)) }
    }

    private fun updateGender(gender: Gender) {
        updateState { copy(user = user?.copy(gender = gender)) }
    }

    /**
     * Сохраняет изменения профиля на сервере, затем кладёт актуального пользователя из ответа локально.
     */
    private fun saveProfile() {
        val userToSave = stateValue.user ?: return
        updateState { copy(isSaving = true, error = null) }
        viewModelScope.launch {
            loadingRepository.emit(true)
            userProfileRepository.updateProfile(
                firstName = userToSave.firstName.trim(),
                lastName = userToSave.lastName.trim(),
                middleName = userToSave.middleName?.trim(),
                phoneNumber = userToSave.phoneNumber?.trim(),
                gender = userToSave.gender
            )
                .onSuccess { updatedUser ->
                    userRepository.saveUser(updatedUser)
                    analytics.trackEvent(AnalyticsEvent.ProfileEditSaved)
                    updateState { copy(user = updatedUser, isSaving = false) }
                    // TODO: Навигация назад или показ сообщения об успехе
                }
                .onFailure {
                    updateState { copy(isSaving = false, error = "Ошибка сохранения") }
                    handleFailure(it)
                }
            loadingRepository.emit(false)
        }
    }

    private fun confirmCrop(cropRect: CropRect) {
        val uri = stateValue.pendingCropUri ?: return
        updateState { copy(pendingCropUri = null) }
        uploadPhoto(uri, cropRect)
    }

    private fun uploadPhoto(uri: Uri, cropRect: CropRect) {
        viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(isSaving = true) }
            loadingRepository.emit(true)
            val bytes = ImageCompressor.compress(
                context = context,
                uri = uri,
                maxWidthPx = ImageCompressor.Preset.AVATAR.maxWidthPx,
                quality = ImageCompressor.Preset.AVATAR.quality,
            ) ?: run {
                updateState { copy(isSaving = false) }
                loadingRepository.emit(false)
                return@launch
            }
            uploadRepository.uploadFile(bytes, "avatar.jpg", "avatar")
                .onSuccess { url ->
                    userProfileRepository.updateAvatarUrl(url, cropRect)
                        .onSuccess { updatedUser ->
                            userRepository.saveUser(updatedUser)
                            updateState { copy(user = updatedUser, isSaving = false) }
                        }
                        .onFailure {
                            updateState { copy(isSaving = false, error = "Ошибка сохранения фото") }
                            handleFailure(it)
                        }
                }
                .onFailure {
                    updateState { copy(isSaving = false, error = "Ошибка загрузки фото") }
                    handleFailure(it)
                }
            loadingRepository.emit(false)
        }
    }

    private fun handleFailure(throwable: Throwable) {
        viewModelScope.launch {
            val code = (throwable as? NetworkException)?.code
            networkErrorRepository.emit(NetworkErrorEvent(code = code, message = throwable.message))
        }
    }
}

/**
 * Действия на экране редактирования профиля.
 */
sealed interface ProfileEditorAction : BaseAction {
    data class UpdateFirstName(val firstName: String) : ProfileEditorAction
    data class UpdateLastName(val lastName: String) : ProfileEditorAction
    data class UpdateMiddleName(val middleName: String) : ProfileEditorAction
    data class UpdatePhoneNumber(val phoneNumber: String) : ProfileEditorAction
    data class UpdateEmail(val email: String) : ProfileEditorAction
    data class UpdateGender(val gender: Gender) : ProfileEditorAction
    data object SaveProfile : ProfileEditorAction
    data class PhotoPicked(val uri: Uri) : ProfileEditorAction
    data class ConfirmCrop(val cropRect: CropRect) : ProfileEditorAction
    data object CancelCrop : ProfileEditorAction
}

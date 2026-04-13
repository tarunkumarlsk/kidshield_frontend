package com.simats.kidshield.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simats.kidshield.models.ChildProfile
import com.simats.kidshield.repositories.ChildRepository
import kotlinx.coroutines.launch

class DashboardViewModel(private val repository: ChildRepository) : ViewModel() {

    private val _children = MutableLiveData<List<ChildProfile>>()
    val children: LiveData<List<ChildProfile>> = _children

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun fetchChildren(parentId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.getChildren(parentId)

                // ✅ FIXED LINE
                _children.value = result ?: emptyList()

                if (result == null) {
                    _error.value = "Failed to load children"
                }

            } catch (e: Exception) {
                _error.value = e.message ?: "An error occurred"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
package com.ritense.formviewmodel.web.rest

import com.ritense.formviewmodel.domain.ViewModelLoader

class TestViewModelLoader : ViewModelLoader<TestViewModel> {
    override fun onLoad(taskInstanceId: String): TestViewModel {
        return TestViewModel()
    }

    override fun supports(formId: String): Boolean {
        return formId == "formId"
    }
}
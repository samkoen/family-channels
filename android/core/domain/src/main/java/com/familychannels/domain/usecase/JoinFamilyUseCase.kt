package com.familychannels.domain.usecase

import com.familychannels.domain.model.ChildProfile
import com.familychannels.domain.repo.FamilyRepository

class JoinFamilyUseCase(private val repo: FamilyRepository) {
    suspend operator fun invoke(familyCode: String): List<ChildProfile> {
        val code = familyCode.trim().uppercase()
        require(code.length in 4..16) { "invalid_family_code" }
        return repo.join(code)
    }
}

package com.example.cdplaya.data

import com.example.cdplaya.data.local.ArtistPictureAssignmentDao
import com.example.cdplaya.data.local.ArtistPictureAssignmentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ArtistPictureAssignment(
    val artistKey: String,
    val normalizedArtistName: String,
    val assetReference: String,
    val updatedAt: Long
)

class ArtistPictureRepository(private val dao: ArtistPictureAssignmentDao) {
    fun observeAll(): Flow<Map<String, ArtistPictureAssignment>> = dao.observeAll().map { rows ->
        rows.associate { row -> row.artistKey to row.toDomain() }
    }

    suspend fun get(artistKey: String): ArtistPictureAssignment? = dao.get(artistKey)?.toDomain()

    suspend fun getAll(): List<ArtistPictureAssignment> = dao.getAll().map { it.toDomain() }

    suspend fun upsert(assignment: ArtistPictureAssignment) {
        dao.upsert(assignment.toEntity())
    }

    suspend fun delete(artistKey: String) = dao.delete(artistKey)

    suspend fun replaceAll(assignments: List<ArtistPictureAssignment>) {
        dao.deleteAll()
        assignments.forEach { dao.upsert(it.toEntity()) }
    }
}

private fun ArtistPictureAssignmentEntity.toDomain() = ArtistPictureAssignment(
    artistKey = artistKey,
    normalizedArtistName = normalizedArtistName,
    assetReference = assetReference,
    updatedAt = updatedAt
)

private fun ArtistPictureAssignment.toEntity() = ArtistPictureAssignmentEntity(
    artistKey = artistKey,
    normalizedArtistName = normalizedArtistName,
    assetReference = assetReference,
    updatedAt = updatedAt
)

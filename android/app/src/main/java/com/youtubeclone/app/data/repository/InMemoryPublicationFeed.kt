package com.youtubeclone.app.data.repository

import com.youtubeclone.app.domain.model.Publication
import com.youtubeclone.app.domain.repository.PublicationFeed
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Annoncée par les dépôts qui publient, une fois le contenu accepté par le serveur. */
@Singleton
class InMemoryPublicationFeed @Inject constructor() : PublicationFeed {
    private val events = MutableSharedFlow<Publication>(extraBufferCapacity = 4)
    override val publications: SharedFlow<Publication> = events.asSharedFlow()

    fun announce(publication: Publication) {
        events.tryEmit(publication)
    }
}

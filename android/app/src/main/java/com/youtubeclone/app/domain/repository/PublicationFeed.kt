package com.youtubeclone.app.domain.repository

import com.youtubeclone.app.domain.model.Publication
import kotlinx.coroutines.flow.Flow

/** Publications faites depuis l'application pendant la session, au fil de l'eau. */
interface PublicationFeed {
    val publications: Flow<Publication>
}

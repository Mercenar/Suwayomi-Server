package suwayomi.anime.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

data class AnimeSourceDataClass(
    val id: String,
    val name: String?,
    val lang: String?,
    val iconUrl: String?,
    val supportsLatest: Boolean?
)

package com.c2m.storyviewer.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


@Parcelize
data class StoryUser(
    val username: String,
    val profilePicUrl: String,
    val stories: ArrayList<Story>
) : Parcelable
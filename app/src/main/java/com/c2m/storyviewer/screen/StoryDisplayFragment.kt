package com.c2m.storyviewer.screen

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.c2m.storyviewer.R
import com.c2m.storyviewer.app.StoryApp.Companion.simpleCache
import com.c2m.storyviewer.customview.StoriesProgressView
import com.c2m.storyviewer.data.Story
import com.c2m.storyviewer.data.StoryUser
import com.c2m.storyviewer.databinding.FragmentStoryDisplayBinding
import com.c2m.storyviewer.screen.MainActivity.Companion.progressState
import com.c2m.storyviewer.utils.OnSwipeTouchListener
import com.c2m.storyviewer.utils.hide
import com.c2m.storyviewer.utils.show
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.android.exoplayer2.util.Util
import java.util.Calendar
import java.util.Locale

class StoryDisplayFragment : Fragment(),
    StoriesProgressView.StoriesListener {

    private val position: Int by
    lazy { arguments?.getInt(EXTRA_POSITION) ?: 0 }

    private var binding: FragmentStoryDisplayBinding? = null

    private var storyUser: StoryUser =
        arguments?.getParcelable(EXTRA_STORY_USER) as? StoryUser ?: StoryUser(
            "", "",
            arrayListOf()
        )

    private var stories: ArrayList<Story> = storyUser.stories


    private var simpleExoPlayer: ExoPlayer? = null
    private lateinit var mediaDataSourceFactory: DataSource.Factory
    private var pageViewOperator: PageViewOperator? = null
    private var counter = 0
    private var pressTime = 0L
    private var limit = 500L
    private var onResumeCalled = false
    private var onVideoPrepared = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentStoryDisplayBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.storyDisplayVideo?.useController = false



        storyUser = arguments?.getParcelable(EXTRA_STORY_USER) as? StoryUser ?: StoryUser(
            "", "",
            arrayListOf()
        )
        stories = storyUser.stories

        updateStory()
        setUpUi()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        this.pageViewOperator = context as PageViewOperator
    }

    override fun onStart() {
        super.onStart()
        counter = restorePosition()
    }

    override fun onResume() {
        super.onResume()
        onResumeCalled = true
        if (stories[counter].isVideo() && !onVideoPrepared) {
            simpleExoPlayer?.playWhenReady = false
            return
        }

        simpleExoPlayer?.seekTo(5)
        simpleExoPlayer?.playWhenReady = true
        if (counter == 0) {
            binding!!.storiesProgressView.startStories()
        } else {
            counter = progressState.get(arguments?.getInt(EXTRA_POSITION) ?: 0)
            binding!!.storiesProgressView.startStories(counter)
        }
    }

    override fun onPause() {
        super.onPause()
        simpleExoPlayer?.playWhenReady = false
        binding!!.storiesProgressView.abandon()
    }

    override fun onComplete() {
        simpleExoPlayer?.release()
        pageViewOperator?.nextPageView()
    }

    override fun onPrev() {
        if (counter - 1 < 0) return
        --counter
        savePosition(counter)
        updateStory()
    }

    override fun onNext() {
        if (stories!!.size <= counter + 1) {
            return
        }
        ++counter
        savePosition(counter)
        updateStory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        simpleExoPlayer?.release()
    }

    private fun updateStory() {
        simpleExoPlayer?.stop()
        if (stories[counter].isVideo()) {
            binding?.storyDisplayVideo?.show()
            binding?.storyDisplayImage?.hide()
            binding?.storyDisplayVideoProgress?.show()
            initializePlayer()
        } else {
            binding?.storyDisplayVideo?.hide()
            binding?.storyDisplayVideoProgress?.hide()
            binding?.storyDisplayImage?.show()
            binding?.storyDisplayImage?.let { Glide.with(this).load(stories[counter].url).into(it) }
        }

        val cal: Calendar = Calendar.getInstance(Locale.ENGLISH).apply {
            timeInMillis = stories[counter].storyDate
        }
        binding?.storyDisplayTime!!.text =
            android.text.format.DateFormat.format("MM-dd-yyyy HH:mm:ss", cal).toString()
    }

    private fun initializePlayer() {
        if (simpleExoPlayer == null) {

            simpleExoPlayer = ExoPlayer.Builder(requireContext()).build()
        } else {
            simpleExoPlayer?.release()
            simpleExoPlayer = null
            simpleExoPlayer = ExoPlayer.Builder(requireContext()).build()
        }


//        val userAgent =
//            Util.getUserAgent(requireContext(), requireContext().getString(R.string.app_name))
//        val cache = SimpleCache(
//            requireContext().cacheDir,
//            LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024) // 100 MB cache
//        )
//
//        val httpDataSourceFactory = DefaultHttpDataSource.Factory().setUserAgent(userAgent)
//        CacheDataSource.Factory().setCache(cache)
        //mediaDataSourceFactory = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(httpDataSourceFactory)

        mediaDataSourceFactory  = DefaultDataSource.Factory(requireContext())
        val mediaSource = ProgressiveMediaSource.Factory(mediaDataSourceFactory).createMediaSource(
            Uri.parse(stories[counter].url)
        )
        simpleExoPlayer?.prepare(mediaSource, false, false)
        if (onResumeCalled) {
            simpleExoPlayer?.playWhenReady = true
        }

        binding?.storyDisplayVideo?.setShutterBackgroundColor(Color.BLACK)
        binding?.storyDisplayVideo?.player = simpleExoPlayer

        simpleExoPlayer?.addListener(object : Player.EventListener {
            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                binding?.storyDisplayVideoProgress?.hide()
                if (counter == stories.size.minus(1)) {
                    pageViewOperator?.nextPageView()
                } else {
                    binding?.storiesProgressView?.skip()
                }
            }


            override fun onLoadingChanged(isLoading: Boolean) {
                super.onLoadingChanged(isLoading)
                if (isLoading) {
                    binding?.storyDisplayVideoProgress?.show()
                    pressTime = System.currentTimeMillis()
                    pauseCurrentStory()
                } else {
                    binding?.storyDisplayVideoProgress!!.hide()
                    binding?.storiesProgressView?.getProgressWithIndex(counter)
                        ?.setDuration(simpleExoPlayer?.duration ?: 8000L)
                    onVideoPrepared = true
                    resumeCurrentStory()
                }
            }
        })
    }

    private fun setUpUi() {
        val touchListener = object : OnSwipeTouchListener(requireActivity()) {
            override fun onSwipeTop() {
                Toast.makeText(requireActivity(), "onSwipeTop", Toast.LENGTH_LONG).show()
            }

            override fun onSwipeBottom() {
                Toast.makeText(requireActivity(), "onSwipeBottom", Toast.LENGTH_LONG).show()
            }

            override fun onClick(view: View) {
                when (view) {
                    binding?.next -> {
                        if (counter == stories!!.size - 1) {
                            pageViewOperator?.nextPageView()
                        } else {
                            binding!!.storiesProgressView.skip()
                        }
                    }

                    binding?.previous -> {
                        if (counter == 0) {
                            pageViewOperator?.backPageView()
                        } else {
                            binding!!.storiesProgressView.reverse()
                        }
                    }
                }
            }

            override fun onLongClick() {
                hideStoryOverlay()
            }

            override fun onTouchView(view: View, event: MotionEvent): Boolean {
                super.onTouchView(view, event)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        pressTime = System.currentTimeMillis()
                        pauseCurrentStory()
                        return false
                    }

                    MotionEvent.ACTION_UP -> {
                        showStoryOverlay()
                        resumeCurrentStory()
                        return limit < System.currentTimeMillis() - pressTime
                    }
                }
                return false
            }
        }
        binding!!.previous.setOnTouchListener(touchListener)
        binding!!.next.setOnTouchListener(touchListener)

        stories?.let {
            binding!!.storiesProgressView.setStoriesCountDebug(
                it.size, position = arguments?.getInt(EXTRA_POSITION) ?: -1
            )
        }
        binding!!.storiesProgressView.setAllStoryDuration(4000L)
        binding!!.storiesProgressView.setStoriesListener(this)

        Glide.with(this).load(storyUser?.profilePicUrl).circleCrop()
            .into(binding!!.storyDisplayProfilePicture)
        binding!!.storyDisplayNick.text = storyUser?.username ?: ""
    }

    fun showStoryOverlay() {
        if (binding!!.storyOverlay.alpha != 0F) return

        binding!!.storyOverlay.animate()
            .setDuration(100)
            .alpha(1F)
            .start()
    }

    fun hideStoryOverlay() {
        if (binding!!.storyOverlay.alpha != 1F) return

        binding!!.storyOverlay.animate()
            .setDuration(200)
            .alpha(0F)
            .start()
    }

    fun savePosition(pos: Int) {
        progressState.put(position, pos)
    }

    fun restorePosition(): Int {
        return progressState.get(position)
    }

    fun pauseCurrentStory() {
        simpleExoPlayer?.playWhenReady = false
        binding?.storiesProgressView?.pause()
    }

    fun resumeCurrentStory() {
        if (onResumeCalled) {
            simpleExoPlayer?.playWhenReady = true
            showStoryOverlay()
            binding?.storiesProgressView?.resume()
        }
    }

    companion object {
        private const val EXTRA_POSITION = "EXTRA_POSITION"
        private const val EXTRA_STORY_USER = "EXTRA_STORY_USER"
        fun newInstance(position: Int, story: StoryUser): StoryDisplayFragment {
            return StoryDisplayFragment().apply {
                arguments = Bundle().apply {
                    putInt(EXTRA_POSITION, position)
                    putParcelable(EXTRA_STORY_USER, story)
                }
            }
        }
    }
}
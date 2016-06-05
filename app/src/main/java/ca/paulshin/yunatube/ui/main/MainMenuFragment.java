package ca.paulshin.yunatube.ui.main;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.TextView;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.util.List;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;
import ca.paulshin.yunatube.BuildConfig;
import ca.paulshin.yunatube.Config;
import ca.paulshin.yunatube.R;
import ca.paulshin.yunatube.data.model.video.Video;
import ca.paulshin.yunatube.receiver.ConnectivityChangeReceiver;
import ca.paulshin.yunatube.ui.adapter.MainVideoAdapter;
import ca.paulshin.yunatube.ui.base.BaseActivity;
import ca.paulshin.yunatube.ui.base.BaseFragment;
import ca.paulshin.yunatube.util.LanguageUtil;
import ca.paulshin.yunatube.util.NetworkUtil;
import ca.paulshin.yunatube.util.ToastUtil;
import ca.paulshin.yunatube.util.events.ConnectivityChangeEvent;
import ca.paulshin.yunatube.widgets.RecyclerViewScrollDetector;
import timber.log.Timber;

public class MainMenuFragment extends BaseFragment implements
		View.OnClickListener,
		MainMenuMvpView {

	private static final boolean VIEW_SHARED = true;
	private static final int FAB_TRANSLATE_DURATION_MILLIS = 200;

	private final Interpolator mInterpolator = new AccelerateDecelerateInterpolator();
	private FirebaseRemoteConfig mFirebaseRemoteConfig;

	// Remote Config keys
	private static final String NOTICE_EN_CONFIG_KEY = "notice_text_en";
	private static final String NOTICE_KO_CONFIG_KEY = "notice_text_ko";
	private static final String FACT_EN_CONFIG_KEY = "fact_text_en";
	private static final String FACT_KO_CONFIG_KEY = "fact_text_ko";

	public interface MainMenuScrollListener {
		void showFab();
		void toggleFab(boolean show);
	}

	@Inject
	MainMenuPresenter mMainMenuPresenter;
	@Inject
	Bus mBus;

	private View mRootView;
	private View mListHeaderView;
	@Bind(R.id.loading)
	View mLoadingView;
	@Bind(R.id.none)
	View mNoneView;
	@Bind(R.id.list)
	RecyclerView mRecyclerView;

	private String mLastNewOrder;
	private MainVideoAdapter mAdapter;
	private ConnectivityChangeReceiver mConnectivityChangeReceiver;
	private MainMenuScrollListener mMainMenuScrollListener;

	public static MainMenuFragment newInstance() {
		MainMenuFragment fragment = new MainMenuFragment();
		return fragment;
	}

	public MainMenuFragment() {
		// Required empty public constructor
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Activity activity = getActivity();
		((BaseActivity)activity).getActivityComponent().inject(this);
		mMainMenuPresenter.attachView(this);
		Timber.tag("MainMenuFragment");

		if (activity instanceof MainMenuScrollListener) {
			mMainMenuScrollListener = (MainMenuScrollListener)activity;
		}

		// Firebase
		mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
		FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
				.setDeveloperModeEnabled(BuildConfig.DEBUG)
				.build();
		mFirebaseRemoteConfig.setConfigSettings(configSettings);
		mFirebaseRemoteConfig.setDefaults(R.xml.remote_config_defaults);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		mRootView = inflater.inflate(R.layout.f_main, container, false);
		ButterKnife.bind(this, mRootView);

		int padding = getAdjustedPadding();

		mListHeaderView = inflater.inflate(R.layout.p_main_header, null);
		mLastNewOrder = "";

		mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
		mAdapter = new MainVideoAdapter(mRecyclerView, mListHeaderView);
		mAdapter.setOnLoadMoreListener(() -> mMainMenuPresenter.getNewVideos(mLastNewOrder));
		mRecyclerView.setAdapter(mAdapter);
		mRecyclerView.setPadding(padding, 0, padding, 0);
		mRecyclerView.addOnScrollListener(new RecyclerViewScrollDetectorImpl());

		ButterKnife.findById(mListHeaderView, R.id.notice_image).setOnClickListener(this);
		ButterKnife.findById(mListHeaderView, R.id.fact_more).setOnClickListener(this);
		ButterKnife.findById(mListHeaderView, R.id.insta_more).setOnClickListener(this);
		ButterKnife.findById(mListHeaderView, R.id.instagram).setOnClickListener(this);
		ButterKnife.findById(mListHeaderView, R.id.facebook).setOnClickListener(this);
		ButterKnife.findById(mListHeaderView, R.id.twitter).setOnClickListener(this);
		ButterKnife.findById(mListHeaderView, R.id.youtube).setOnClickListener(this);

		loadData();

		return mRootView;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		mMainMenuPresenter.detachView();
		mMainMenuScrollListener = null;
	}

	/**
	 * Load instagram feed and videos by making api calls if network is connected
	 */
	private void loadData() {
		fetchNotice();

		if (NetworkUtil.isNetworkConnected(getActivity())) {
			mNoneView.setVisibility(View.GONE);
			mLoadingView.setVisibility(View.VISIBLE);

			mMainMenuPresenter.getNewVideos(mLastNewOrder);
		} else {
			mRecyclerView.setVisibility(View.GONE);
			mLoadingView.setVisibility(View.GONE);
			mNoneView.setVisibility(View.VISIBLE);
		}
	}

	private void fetchNotice() {
		displayNotice(true);

		long cacheExpiration = 3600; // 1 hour in seconds.
		// If in developer mode cacheExpiration is set to 0 so each fetch will retrieve values from
		// the server.
		if (mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
			cacheExpiration = 0;
		}

		// cacheExpirationSeconds is set to cacheExpiration here, indicating that any previously
		// fetched and cached config would be considered expired because it would have been fetched
		// more than cacheExpiration seconds ago. Thus the next fetch would go to the server unless
		// throttling is in progress. The default expiration duration is 43200 (12 hours).
		mFirebaseRemoteConfig.fetch(cacheExpiration)
				.addOnCompleteListener((task) -> {
					if (task.isSuccessful()) {
						Timber.d("Fetch Succeeded");
						// Once the config is successfully fetched it must be activated before newly fetched
						// values are returned.
						mFirebaseRemoteConfig.activateFetched();
					} else {
						Timber.d("Fetch failed");
					}
					displayNotice(false);
				});
	}

	private void displayNotice(boolean isLoading) {
		TextView noticeView = ButterKnife.findById(mListHeaderView, R.id.notice_text);
		TextView factView = ButterKnife.findById(mListHeaderView, R.id.fact_text);

		String noticeText;
		String factText;
		if (isLoading) {
			noticeText = getString(R.string.loading);
			factText = getString(R.string.loading);
		} else {
			noticeText = mFirebaseRemoteConfig.getString(LanguageUtil.isKorean() ? NOTICE_KO_CONFIG_KEY : NOTICE_EN_CONFIG_KEY);
			factText = mFirebaseRemoteConfig.getString(LanguageUtil.isKorean() ? FACT_KO_CONFIG_KEY : FACT_EN_CONFIG_KEY);
		}

		noticeView.setText(noticeText);
		factView.setText(factText);
	}

	@Override
	public void onResume() {
		super.onResume();

		mConnectivityChangeReceiver = new ConnectivityChangeReceiver();
		getActivity().registerReceiver(mConnectivityChangeReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
		mBus.register(this);
	}

	@Override
	public void onPause() {
		super.onPause();

		getActivity().unregisterReceiver(mConnectivityChangeReceiver);
		mBus.unregister(this);
	}

	/*****
	 * MVP View methods implementation
	 *****/

	@Override
	public void showError() {
		//TODO
		ButterKnife.findById(mListHeaderView, R.id.notice_section).setVisibility(View.GONE);
		ButterKnife.findById(mListHeaderView, R.id.fact_section).setVisibility(View.GONE);
	}

	@Override
	public void updateVideos(List<Video> videos) {
		if (!videos.isEmpty()) {
			mLastNewOrder = videos.get(videos.size() - 1).newOrder;

			if (TextUtils.equals(mLastNewOrder, "0")) {
				// After reaching the last one, deactivate loadmore
				mAdapter.setOnLoadMoreListener(null);
			}
			mAdapter.addVideos(videos);
			mAdapter.setLoaded();
			mAdapter.notifyDataSetChanged();
		}

		populateListItems();
	}

	private void populateListItems() {
		mLoadingView.postDelayed(() -> mLoadingView.setVisibility(View.GONE), 1000);
		mRecyclerView.postDelayed(() -> mRecyclerView.setVisibility(View.VISIBLE), 1000);
		mMainMenuScrollListener.showFab();
	}

	public void onInstaClicked(View view) {
		Activity activity = getActivity();
		String photoUrl = (String) view.getTag(R.id.insta_photo_url);
		String videoUrl = (String) view.getTag(R.id.insta_video_url);
		Integer videoWidth = (Integer) view.getTag(R.id.insta_video_width);
		Integer videoHeight = (Integer) view.getTag(R.id.insta_video_height);
		Intent intent;

		if (videoUrl == null) {
			intent = new Intent(activity, InstaPhotoActivity.class);
			intent.putExtra(InstaPhotoActivity.EXTRA_INSTA_PHOTO_URL, photoUrl);
		} else {
			intent = new Intent(activity, InstaVideoActivity.class);
			intent.putExtra(InstaVideoActivity.EXTRA_INSTA_PHOTO_URL, photoUrl);
			intent.putExtra(InstaVideoActivity.EXTRA_INSTA_VIDEO_URL, videoUrl);
			intent.putExtra(InstaVideoActivity.EXTRA_INSTA_VIDEO_WIDTH, videoWidth);
			intent.putExtra(InstaVideoActivity.EXTRA_INSTA_VIDEO_HEIGHT, videoHeight);
		}

//		ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(activity, view, "insta");
//		activity.startActivity(intent, options.toBundle());

		startActivity(intent);
		activity.overridePendingTransition(R.anim.start_enter, R.anim.start_exit);
	}

	@Override
	public void onClick(View v) {
		String url;
		Intent browserIntent;

		switch(v.getId()) {
			case R.id.notice_image:
				startActivity(new Intent(getActivity(), SettingsActivity.class));
				getActivity().overridePendingTransition(R.anim.start_enter, R.anim.start_exit);
				break;
			case R.id.instagram:
				url = "http://" + getString(R.string.links_official_instagram_url);
				browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
				startActivity(browserIntent);
				break;
			case R.id.facebook:
				url = "http://" + getString(R.string.links_official_facebook_url);
				browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
				startActivity(browserIntent);
				break;
			case R.id.twitter:
				url = "http://" + getString(R.string.links_official_yunaaaa_url);
				browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
				startActivity(browserIntent);
				break;
			case R.id.youtube:
				url = "http://" + getString(R.string.links_official_youtube_url);
				browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
				startActivity(browserIntent);
				break;
			case R.id.fact_more:
				Uri uri = Uri.parse(Config.YUNAFACT);
				startActivity(new Intent(Intent.ACTION_VIEW, uri));
				getActivity().overridePendingTransition(R.anim.start_enter, R.anim.start_exit);
				sendScreen("fact - android");
				break;
			case R.id.insta_more:
				startActivity(new Intent(getActivity(), InstaFeedActivity.class));
				getActivity().overridePendingTransition(R.anim.start_enter, R.anim.start_exit);
				break;
			case R.id.insta_frame_0:
			case R.id.insta_frame_1:
			case R.id.insta_frame_2:
			case R.id.insta_frame_3:
			case R.id.insta_frame_4:
			case R.id.insta_frame_5:
			case R.id.insta_frame_6:
			case R.id.insta_frame_7:
			case R.id.insta_frame_8:
			case R.id.insta_frame_9:
				onInstaClicked(((ViewGroup) v).getChildAt(0));
				break;
		}
	}

	private class RecyclerViewScrollDetectorImpl extends RecyclerViewScrollDetector {
		@Override
		public void onScrollDown() {
			if (mMainMenuScrollListener != null) {
				mMainMenuScrollListener.toggleFab(true);
			}
		}

		@Override
		public void onScrollUp() {
			if (mMainMenuScrollListener != null) {
				mMainMenuScrollListener.toggleFab(false);
			}
		}
	}

	@Subscribe
	public void onConnectivityChange(ConnectivityChangeEvent status) {
		if (status.networkEnabled) {
			loadData();
		} else {
			//TODO
			ToastUtil.toast(getActivity(), "No internet");
		}
	}
}

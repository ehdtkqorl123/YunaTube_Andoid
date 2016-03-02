package ca.paulshin.yunatube.ui.main;

import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;

import butterknife.Bind;
import butterknife.ButterKnife;
import ca.paulshin.yunatube.R;
import ca.paulshin.yunatube.data.model.flickr.CollectionItem;
import ca.paulshin.yunatube.ui.adapter.SectionLayoutAdapter;
import ca.paulshin.yunatube.ui.base.BaseActivity;
import ca.paulshin.yunatube.util.ResourceUtil;

public class PhotoSectionActivity extends BaseActivity {
	public static final String EXTRA_COLLECTION = "collection";

	private CollectionItem collection;

	@Bind(R.id.grid)
	public RecyclerView mRecyclerView;
	@Bind(R.id.loading)
	public View mLoadingView;

	@Override
	protected String getScreenName() {
		return "album_set - android";
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.a_photo_section);
		ButterKnife.bind(this);

		collection = getIntent().getParcelableExtra(EXTRA_COLLECTION);

		final Toolbar toolbar = getActionBarToolbar();
		toolbar.setNavigationIcon(R.drawable.ic_up);
		toolbar.setNavigationOnClickListener((__) -> finish());
		setTitle(collection.title);

		mRecyclerView.setLayoutManager(new GridLayoutManager(this, ResourceUtil.getInteger(R.integer.photo_sections_columns)));

		if (collection.set.size() > 0) {
			SectionLayoutAdapter adapter = new SectionLayoutAdapter(PhotoSectionActivity.this, collection.set);
			mLoadingView.setVisibility(View.GONE);
			mRecyclerView.setAdapter(adapter);
		}
	}
}
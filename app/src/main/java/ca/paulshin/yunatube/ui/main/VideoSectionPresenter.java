package ca.paulshin.yunatube.ui.main;

import java.util.List;

import javax.inject.Inject;

import ca.paulshin.yunatube.data.DataManager;
import ca.paulshin.yunatube.data.model.video.Section;
import ca.paulshin.yunatube.ui.base.BasePresenter;
import ca.paulshin.yunatube.util.CollectionUtil;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

public class VideoSectionPresenter extends BasePresenter<VideoSectionMvpView> {

    private final DataManager mDataManager;
    private Subscription mSubscription;

    @Inject
    public VideoSectionPresenter(DataManager dataManager) {
        mDataManager = dataManager;
    }

    @Override
    public void attachView(VideoSectionMvpView mvpView) {
        super.attachView(mvpView);
    }

    @Override
    public void detachView() {
        super.detachView();
        if (mSubscription != null) mSubscription.unsubscribe();
    }

    public void getSections(String cid) {
        checkViewAttached();

        mSubscription = mDataManager.getSections(cid)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new Subscriber<List<Section>>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        Timber.e(e, "There was an error loading search data.");
                        getMvpView().showError();
                    }

                    @Override
                    public void onNext(List<Section> sections) {
                        if (!CollectionUtil.isEmpty(sections)) {
                            getMvpView().showSections(sections);
                        } else {
                            getMvpView().showError();
                        }
                    }
                });
    }
}
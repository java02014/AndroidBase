package com.android.base.db;

import android.content.Context;

import com.alibaba.fastjson.JSON;
import com.android.base.db.impl.DbCallBack;
import com.android.base.util.RxUtil;
import com.apkfuns.logutils.LogUtils;

import java.util.List;
import java.util.concurrent.Callable;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

/**
 * 提供同步与异步两种方式读写数据库
 * 如果使用异步方式读写数据库，必须调用{@link BaseRxDao#subscribe()}方法订阅，
 * 调用{@link BaseRxDao#unsubscribe()}方法取消订阅
 */
public abstract class BaseRxDao<T, Integer> extends BaseOrmLiteDao<T, Integer> {

    private CompositeSubscription mSubscriptions;
    private boolean mCache;
    private Class<T> mClazz;
    private String mTableName;

    public BaseRxDao(Context context, Class<T> cls) {
        this(context, cls, true);
    }

    /**
     * @param cls     表结构clazz
     * @param cache   是否缓存，如果设置缓存，数据查询将优先读取缓存
     */
    public BaseRxDao(Context context, Class<T> cls, boolean cache) {
        super(context);
        this.mClazz = cls;
        this.mCache = cache;
        mTableName = DatabaseUtil.extractTableName(cls);
    }

    /**
     * 订阅读写操作的返回结果
     * <p/>
     * 注意：调用{@link BaseRxDao#unsubscribe()}方法后，如果需要重新订阅读写操作，必须调用此方法
     */
    public void subscribe() {
        mSubscriptions = RxUtil.getNewCompositeSubIfUnsubscribed(mSubscriptions);
    }

    /**
     * 异步读写后，必须调用此方法取消订阅
     */
    public void unsubscribe() {
        RxUtil.unsubscribeIfNotNull(mSubscriptions);
    }

    /**
     * 增加一条记录
     */
    public boolean insert(T t) {
        boolean result = super.insert(t);
        if (result) {
            DbCache.getInstance().clearByTable(mTableName);
        }
        return result;
    }

    /**
     * 增加一条记录
     */
    public Observable insertAsync(final T t, final DbCallBack listener) {
        Observable observable = subscribe(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return insert(t);
            }
        }, new Action1<Boolean>() {
            @Override
            public void call(Boolean result) {
                listener.onComplete(result);
            }
        });
        return observable;
    }

    /**
     * 批量插入;
     */
    public boolean insertForBatch(List<T> list) {
        boolean result = super.insertForBatch(list);
        if (result) {
            DbCache.getInstance().clearByTable(mTableName);
        }
        return result;
    }

    /**
     * 批量插入
     */
    public Observable insertForBatchAsync(final List<T> list, final DbCallBack listener) {
        Observable observable = subscribe(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return insertForBatch(list);
            }
        }, new Action1<Boolean>() {
            @Override
            public void call(Boolean result) {
                listener.onComplete(result);
            }
        });
        return observable;
    }

    /**
     * 清空数据
     */
    public boolean clearTableData() {
        boolean result = super.clearTableData();
        if (result) {
            DbCache.getInstance().clearByTable(mTableName);
        }
        return result;
    }

    /**
     * 清空数据
     */
    public Observable clearTableDataAsync(final DbCallBack listener) {
        Observable observable = subscribe(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return clearTableData();
            }
        }, new Action1<Boolean>() {
            @Override
            public void call(Boolean result) {
                listener.onComplete(result);
            }
        });
        return observable;
    }

    /**
     * 根据id删除记录
     */
    public boolean deleteById(Integer id) {
        boolean result = super.deleteById(id);
        if (result) {
            DbCache.getInstance().clearByTable(mTableName);
        }
        return result;
    }

    /**
     * 根据id删除记录
     */
    public Observable deleteByIdAsync(final Integer id, final DbCallBack listener) {
        Observable observable = subscribe(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return deleteById(id);
            }
        }, new Action1<Boolean>() {
            @Override
            public void call(Boolean result) {
                listener.onComplete(result);
            }
        });
        return observable;
    }

    public List<T> queryForAll() {
        if (!mCache) {
            return super.queryForAll();
        }
        String json = DbCache.getInstance().getCache(mTableName, "queryForAll");
        List<T> result = JSON.parseArray(json, mClazz);
        if (result != null) {
            LogUtils.d("---------query from cache--");
            return result;
        }
        result = super.queryForAll();
        DbCache.getInstance().addCache(mTableName, "queryForAll", result);
        return result;
    }

    public Observable queryForAllObservable() {
        return getDbObservable(new Callable() {
            @Override
            public Object call() throws Exception {
                return queryForAll();
            }
        });
    }

    public Observable queryForAllAsync(final DbCallBack listener) {
        Observable observable = subscribe(new Callable<List<T>>() {
            @Override
            public List<T> call() {
                return queryForAll();
            }
        }, new Action1<List<T>>() {
            @Override
            public void call(List<T> result) {
                listener.onComplete(result);
            }
        });

        return observable;
    }

    public <T> Observable<T> subscribe(Callable<T> callable, Action1<T> action) {
        Observable<T> observable = getDbObservable(callable);
        Subscription subscription = observable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(action);
        if (mSubscriptions == null) {
            throw new RuntimeException("Do you call subscribe()");
        }
        mSubscriptions.add(subscription);
        return observable;
    }


    private <T> Observable<T> getDbObservable(final Callable<T> func) {
        return Observable.create(
                new Observable.OnSubscribe<T>() {
                    @Override
                    public void call(Subscriber<? super T> subscriber) {
                        try {
                            subscriber.onNext(func.call());
                        } catch (Exception ex) {
                            LogUtils.e("Error reading from the database", ex);
                        }
                    }
                });
    }

    private Class<T> getClazz(){
        return mClazz;
    }
}

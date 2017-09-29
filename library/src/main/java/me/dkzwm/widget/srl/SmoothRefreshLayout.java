package me.dkzwm.widget.srl;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.SystemClock;
import android.support.annotation.FloatRange;
import android.support.annotation.IntDef;
import android.support.annotation.IntRange;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.NestedScrollingChild;
import android.support.v4.view.NestedScrollingChildHelper;
import android.support.v4.view.NestedScrollingParent;
import android.support.v4.view.NestedScrollingParentHelper;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.AbsListView;
import android.widget.Scroller;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.dkzwm.widget.srl.extra.IRefreshView;
import me.dkzwm.widget.srl.extra.header.MaterialHeader;
import me.dkzwm.widget.srl.gesture.GestureDetector;
import me.dkzwm.widget.srl.gesture.IGestureDetector;
import me.dkzwm.widget.srl.gesture.OnGestureListener;
import me.dkzwm.widget.srl.indicator.DefaultIndicator;
import me.dkzwm.widget.srl.indicator.IIndicator;
import me.dkzwm.widget.srl.utils.BoundaryUtil;
import me.dkzwm.widget.srl.utils.SRLog;
import me.dkzwm.widget.srl.utils.ScrollCompat;

import static android.view.View.MeasureSpec.makeMeasureSpec;

/**
 * Created by dkzwm on 2017/5/18.
 * <p>
 * Part of the code comes from @see <a href="https://github.com/liaohuqiu/android-Ultra-Pull-To-Refresh">
 * android-Ultra-Pull-To-Refresh</a><br/>
 * 部分代码实现来自 @see <a href="https://github.com/liaohuqiu">LiaoHuQiu</a> 的UltraPullToRefresh项目</p>
 * Support NestedScroll feature;<br/>
 * Support OverScroll feature;<br/>
 * Support Refresh and LoadMore feature;<br/>
 * Support AutoRefresh feature;<br/>
 * Support scroll to bottom to auto load more feature;<br/>
 * Support MultiState feature;<br/>
 *
 * @author dkzwm
 */
public class SmoothRefreshLayout extends ViewGroup implements OnGestureListener, NestedScrollingChild,
        NestedScrollingParent, ViewTreeObserver.OnScrollChangedListener {
    //state
    public static final byte STATE_CONTENT = 0;
    public static final byte STATE_ERROR = 1;
    public static final byte STATE_EMPTY = 2;
    public static final byte STATE_CUSTOM = 3;
    //status
    public static final byte SR_STATUS_INIT = 1;
    public static final byte SR_STATUS_PREPARE = 2;
    public static final byte SR_STATUS_REFRESHING = 3;
    public static final byte SR_STATUS_LOADING_MORE = 4;
    public static final byte SR_STATUS_COMPLETE = 5;
    //fresh view status
    public static final byte SR_VIEW_STATUS_INIT = 1;
    public static final byte SR_VIEW_STATUS_HEADER_IN_PROCESSING = 2;
    public static final byte SR_VIEW_STATUS_FOOTER_IN_PROCESSING = 3;

    protected static final String TAG = "SmoothRefreshLayout";
    //local
    private static final byte FLAG_AUTO_REFRESH_AT_ONCE = 0x01;
    private static final byte FLAG_AUTO_REFRESH_BUT_LATER = 0x01 << 1;
    private static final byte FLAG_ENABLE_NEXT_AT_ONCE = 0x01 << 2;
    private static final byte FLAG_ENABLE_OVER_SCROLL = 0x01 << 3;
    private static final byte FLAG_ENABLE_KEEP_REFRESH_VIEW = 0x01 << 4;
    private static final byte FLAG_ENABLE_PIN_CONTENT_VIEW = 0x01 << 5;
    private static final byte FLAG_ENABLE_PULL_TO_REFRESH = 0x01 << 6;
    private static final int FLAG_ENABLE_PIN_REFRESH_VIEW_WHILE_LOADING = 0x01 << 7;
    private static final int FLAG_ENABLE_HEADER_DRAWER_STYLE = 0x01 << 8;
    private static final int FLAG_ENABLE_FOOTER_DRAWER_STYLE = 0x01 << 9;
    private static final int FLAG_DISABLE_PERFORM_LOAD_MORE = 0x01 << 10;
    private static final int FLAG_ENABLE_LOAD_MORE_NO_MORE_DATA = 0x01 << 11;
    private static final int FLAG_DISABLE_LOAD_MORE = 0x01 << 12;
    private static final int FLAG_DISABLE_PERFORM_REFRESH = 0x01 << 13;
    private static final int FLAG_DISABLE_REFRESH = 0x01 << 14;
    private static final int FLAG_ENABLE_WHEN_SCROLLING_TO_BOTTOM_TO_PERFORM_LOAD_MORE = 0x01 << 15;
    private static final int FLAG_ENABLE_WHEN_SCROLLING_TO_TOP_TO_PERFORM_REFRESH = 0x01 << 16;
    private static final int FLAG_ENABLE_INTERCEPT_EVENT_WHILE_LOADING = 0x01 << 17;
    private static final int FLAG_DISABLE_WHEN_HORIZONTAL_MOVE = 0x01 << 18;
    private static final int FLAG_ENABLE_HIDE_HEADER_VIEW = 0x01 << 19;
    private static final int FLAG_ENABLE_HIDE_FOOTER_VIEW = 0x01 << 20;
    private static final int FLAG_ENABLE_CHECK_FINGER_INSIDE = 0x01 << 21;
    private static final byte MASK_AUTO_REFRESH = 0x03;
    private static final int MASK_DISABLE_PERFORM_LOAD_MORE = 0x07 << 10;
    private static final int MASK_DISABLE_PERFORM_REFRESH = 0x03 << 13;
    private static final int[] LAYOUT_ATTRS = new int[]{
            android.R.attr.enabled
    };
    private static final Interpolator mQuinticInterpolator = new Interpolator() {
        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };
    protected static boolean sDebug = false;
    private static IRefreshViewCreator sCreator;
    private final List<View> mCachedViews = new ArrayList<>(1);
    private final NestedScrollingParentHelper mNestedScrollingParentHelper;
    private final NestedScrollingChildHelper mNestedScrollingChildHelper;
    private final int[] mParentScrollConsumed = new int[2];
    private final int[] mParentOffsetInWindow = new int[2];
    @State
    protected int mState = STATE_CONTENT;
    protected int mPreviousState = -1;
    protected IRefreshView mHeaderView;
    protected IRefreshView mFooterView;
    protected IIndicator mIndicator;
    protected OnRefreshListener mRefreshListener;
    protected OnStateChangedListener mStateChangedListener;
    protected byte mStatus = SR_STATUS_INIT;
    protected byte mViewStatus = SR_VIEW_STATUS_INIT;
    protected boolean mNeedNotifyRefreshComplete = true;
    protected boolean mDelayedRefreshComplete = false;
    protected boolean mAutomaticActionUseSmoothScroll = false;
    protected boolean mAutomaticActionInScrolling = false;
    protected boolean mAutomaticActionTriggered = true;
    protected long mLoadingMinTime = 500;
    protected long mLoadingStartTime = 0;
    protected int mDurationToCloseHeader = 500;
    protected int mDurationToCloseFooter = 500;
    protected View mTargetView;
    protected View mContentView;
    protected View mEmptyView;
    protected View mErrorView;
    protected View mCustomView;
    protected View mLoadMoreScrollTargetView;
    protected LayoutInflater mInflater;
    protected int mContentResId = View.NO_ID;
    protected int mErrorLayoutResId = View.NO_ID;
    protected int mEmptyLayoutResId = View.NO_ID;
    protected int mCustomLayoutResId = View.NO_ID;
    private int mFlag = FLAG_DISABLE_LOAD_MORE;
    private Interpolator mSpringInterpolator;
    private Interpolator mOverScrollInterpolator;
    private IGestureDetector mGestureDetector;
    private OnChildScrollUpCallback mScrollUpCallback;
    private OnChildScrollDownCallback mScrollDownCallback;
    private OnLoadMoreScrollCallback mLoadMoreScrollCallback;
    private OnPerformAutoLoadMoreCallBack mAutoLoadMoreCallBack;
    private OnFingerInsideHorViewCallback mFingerInsideHorizontalViewCallback;
    private List<OnUIPositionChangedListener> mUIPositionChangedListeners;
    private ScrollChecker mScrollChecker;
    private OverScrollChecker mOverScrollChecker;
    private MotionEvent mLastMoveEvent;
    private DelayToRefreshComplete mDelayToRefreshComplete;
    private RefreshCompleteHook mHeaderRefreshCompleteHook;
    private RefreshCompleteHook mFooterRefreshCompleteHook;
    private ViewTreeObserver mTargetViewTreeObserver;
    private ValueAnimator mChangeStateAnimator;
    private boolean mHasSendCancelEvent = false;
    private boolean mHasSendDownEvent = false;
    private boolean mDealHorizontalMove = false;
    private boolean mPreventForHorizontal = false;
    private boolean mIsLastRefreshSuccessful = true;
    private boolean mNestedScrollInProgress = false;
    private boolean mViewsZAxisNeedReset = true;
    private boolean mAutoRefreshBeenSendTouchEvent = false;
    private boolean mNeedInterceptTouchEventInOnceTouch = false;
    private boolean mIsLastOverScrollCanNotAbort = false;
    private boolean mNeedFilterScrollEvent = false;
    private boolean mIsFingerInsideHorizontalView = false;
    private float mOverScrollDurationRatio = 0.5f;
    private int mMaxOverScrollDuration = 500;
    private int mMinOverScrollDuration = 150;
    private int mDurationOfBackToHeaderHeight = 200;
    private int mDurationOfBackToFooterHeight = 200;
    private int mTouchSlop;
    private int mTouchPointerId;

    public SmoothRefreshLayout(Context context) {
        this(context, null);
    }

    public SmoothRefreshLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SmoothRefreshLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mIndicator = new DefaultIndicator();
        mInflater = LayoutInflater.from(context);
        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.SmoothRefreshLayout, 0, 0);
        if (arr != null) {
            mContentResId = arr.getResourceId(R.styleable.SmoothRefreshLayout_sr_content, mContentResId);
            float resistance = arr.getFloat(R.styleable
                    .SmoothRefreshLayout_sr_resistance, IIndicator.DEFAULT_RESISTANCE);
            mIndicator.setResistance(resistance);
            mIndicator.setResistanceOfHeader(arr.getFloat(R.styleable
                    .SmoothRefreshLayout_sr_resistance_of_header, resistance));
            mIndicator.setResistanceOfFooter(arr.getFloat(R.styleable
                    .SmoothRefreshLayout_sr_resistance_of_footer, resistance));

            mDurationOfBackToHeaderHeight = arr.getInt(R.styleable
                            .SmoothRefreshLayout_sr_duration_of_back_to_keep_refresh_pos,
                    mDurationOfBackToHeaderHeight);
            mDurationOfBackToFooterHeight = arr.getInt(R.styleable
                            .SmoothRefreshLayout_sr_duration_of_back_to_keep_refresh_pos,
                    mDurationOfBackToFooterHeight);
            mDurationOfBackToHeaderHeight = arr.getInt(R.styleable
                            .SmoothRefreshLayout_sr_duration_of_back_to_keep_header_pos,
                    mDurationOfBackToHeaderHeight);
            mDurationOfBackToFooterHeight = arr.getInt(R.styleable
                            .SmoothRefreshLayout_sr_duration_of_back_to_keep_footer_pos,
                    mDurationOfBackToFooterHeight);

            mDurationToCloseHeader = arr.getInt(R.styleable
                            .SmoothRefreshLayout_sr_duration_to_close_of_refresh,
                    mDurationToCloseHeader);
            mDurationToCloseFooter = arr.getInt(R.styleable
                            .SmoothRefreshLayout_sr_duration_to_close_of_refresh,
                    mDurationToCloseFooter);
            mDurationToCloseHeader = arr.getInt(R.styleable
                    .SmoothRefreshLayout_sr_duration_to_close_of_header, mDurationToCloseHeader);
            mDurationToCloseFooter = arr.getInt(R.styleable
                    .SmoothRefreshLayout_sr_duration_to_close_of_footer, mDurationToCloseFooter);

            //ratio
            float ratio = arr.getFloat(R.styleable.
                    SmoothRefreshLayout_sr_ratio_of_refresh_height_to_refresh, IIndicator
                    .DEFAULT_RATIO_OF_REFRESH_VIEW_HEIGHT_TO_REFRESH);
            mIndicator.setRatioOfRefreshViewHeightToRefresh(ratio);
            mIndicator.setRatioOfHeaderHeightToRefresh(arr.getFloat(R.styleable
                    .SmoothRefreshLayout_sr_ratio_of_header_height_to_refresh, ratio));
            mIndicator.setRatioOfFooterHeightToRefresh(arr.getFloat(R.styleable
                    .SmoothRefreshLayout_sr_ratio_of_footer_height_to_refresh, ratio));

            ratio = arr.getFloat(R.styleable.
                    SmoothRefreshLayout_sr_offset_ratio_to_keep_refresh_while_Loading, IIndicator
                    .DEFAULT_RATIO_OF_REFRESH_VIEW_HEIGHT_TO_REFRESH);
            mIndicator.setOffsetRatioToKeepHeaderWhileLoading(ratio);
            mIndicator.setOffsetRatioToKeepFooterWhileLoading(ratio);
            mIndicator.setOffsetRatioToKeepHeaderWhileLoading(arr.getFloat(R.styleable
                    .SmoothRefreshLayout_sr_offset_ratio_to_keep_header_while_Loading, ratio));
            mIndicator.setOffsetRatioToKeepFooterWhileLoading(arr.getFloat(R.styleable
                    .SmoothRefreshLayout_sr_offset_ratio_to_keep_footer_while_Loading, ratio));

            //max move ratio of height
            ratio = arr.getFloat(R.styleable.
                    SmoothRefreshLayout_sr_can_move_the_max_ratio_of_refresh_height, IIndicator
                    .DEFAULT_CAN_MOVE_THE_MAX_RATIO_OF_REFRESH_VIEW_HEIGHT);
            mIndicator.setCanMoveTheMaxRatioOfRefreshViewHeight(ratio);
            mIndicator.setCanMoveTheMaxRatioOfHeaderHeight(arr.getFloat(R.styleable
                    .SmoothRefreshLayout_sr_can_move_the_max_ratio_of_header_height, ratio));
            mIndicator.setCanMoveTheMaxRatioOfFooterHeight(arr.getFloat(R.styleable
                    .SmoothRefreshLayout_sr_can_move_the_max_ratio_of_footer_height, ratio));

            setEnableKeepRefreshView(arr.getBoolean(R.styleable
                    .SmoothRefreshLayout_sr_enable_keep_refresh_view, true));
            setEnablePinContentView(arr.getBoolean(R.styleable
                    .SmoothRefreshLayout_sr_enable_pin_content, false));
            setEnableOverScroll(arr.getBoolean(R.styleable
                    .SmoothRefreshLayout_sr_enable_over_scroll, true));
            setEnablePullToRefresh(arr.getBoolean(R.styleable
                    .SmoothRefreshLayout_sr_enable_pull_to_refresh, false));

            setDisableRefresh(!arr.getBoolean(R.styleable.SmoothRefreshLayout_sr_enable_refresh,
                    true));
            setDisableLoadMore(!arr.getBoolean(R.styleable
                    .SmoothRefreshLayout_sr_enable_load_more, false));

            mErrorLayoutResId = arr.getResourceId(R.styleable.SmoothRefreshLayout_sr_error_layout,
                    NO_ID);
            mEmptyLayoutResId = arr.getResourceId(R.styleable.SmoothRefreshLayout_sr_empty_layout,
                    NO_ID);
            mCustomLayoutResId = arr.getResourceId(R.styleable.SmoothRefreshLayout_sr_custom_layout,
                    NO_ID);
            @State
            int state = arr.getInt(R.styleable.SmoothRefreshLayout_sr_state, STATE_CONTENT);
            mState = state;
            arr.recycle();
            arr = context.obtainStyledAttributes(attrs, LAYOUT_ATTRS, 0, 0);
            setEnabled(arr.getBoolean(0, true));
            arr.recycle();
        } else {
            setEnablePullToRefresh(true);
            setEnableKeepRefreshView(true);
        }
        final ViewConfiguration conf = ViewConfiguration.get(getContext());
        mTouchSlop = conf.getScaledTouchSlop();
        mGestureDetector = new GestureDetector(context, this);
        mScrollChecker = new ScrollChecker();
        mOverScrollChecker = new OverScrollChecker();
        mSpringInterpolator = mQuinticInterpolator;
        mOverScrollInterpolator = new DecelerateInterpolator(1.22f);
        //Nested scrolling
        mNestedScrollingChildHelper = new NestedScrollingChildHelper(this);
        mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);
        setNestedScrollingEnabled(true);
    }

    public static void debug(boolean debug) {
        sDebug = debug;
    }

    public static boolean isDebug() {
        return sDebug;
    }

    /**
     * Set the static refresh view creator, if the refresh view is null and the frame be
     * needed the refresh view,frame will use this creator to create refresh view<br/>
     * <p>
     * 设置默认的刷新视图构造器，当刷新视图为null且需要使用刷新视图时，Frame会使用该构造器构造刷新视图
     *
     * @param creator The static refresh view creator
     */
    public static void setDefaultCreator(IRefreshViewCreator creator) {
        sCreator = creator;
    }

    @Override
    final public void addView(View child) {
        addView(child, -1);
    }

    @Override
    final public void addView(View child, int index) {
        if (child == null) {
            throw new IllegalArgumentException("Cannot add a null child view to a ViewGroup");
        }
        ViewGroup.LayoutParams params = child.getLayoutParams();
        if (params == null) {
            params = generateDefaultLayoutParams();
            if (params == null) {
                throw new IllegalArgumentException("generateDefaultLayoutParams() cannot return null");
            }
        }
        addView(child, index, params);
    }

    @Override
    final public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        ensureFreshView(child);
    }

    @Override
    final public void addView(View child, ViewGroup.LayoutParams params) {
        addView(child, -1, params);
    }

    @Override
    final public void addView(View child, int width, int height) {
        final ViewGroup.LayoutParams params = generateDefaultLayoutParams();
        if (params == null) {
            throw new IllegalArgumentException("generateDefaultLayoutParams() cannot return null");
        }
        params.width = width;
        params.height = height;
        addView(child, -1, params);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled)
            reset();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        destroy();
    }

    @Override
    public void invalidate() {
        super.invalidate();
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = getChildCount();
        if (count == 0)
            return;
        ensureTargetView();
        int maxHeight = 0;
        int maxWidth = 0;
        int childState = 0;
        final int paddingLeft = getPaddingLeft();
        final int paddingRight = getPaddingRight();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE)
                continue;
            final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            if (mHeaderView != null && child == mHeaderView.getView()) {
                if (isDisabledRefresh() || isEnabledHideHeaderView())
                    continue;
                if (mHeaderView.getStyle() == IRefreshView.STYLE_DEFAULT) {
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                } else {
                    final int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
                            paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin, lp.width);
                    final int heightSpec;
                    if (isMovingHeader()) {
                        heightSpec = makeMeasureSpec(mIndicator.getCurrentPosY()
                                + lp.topMargin, MeasureSpec.EXACTLY);
                    } else {
                        heightSpec = makeMeasureSpec(0, MeasureSpec.EXACTLY);
                    }
                    child.measure(childWidthMeasureSpec, heightSpec);
                }
                if (mHeaderView.getCustomHeight() > 0) {
                    mIndicator.setHeaderHeight(mHeaderView.getCustomHeight());
                } else {
                    if (mHeaderView.getStyle() == IRefreshView.STYLE_SCALE)
                        throw new IllegalArgumentException("If header view's type is " +
                                "STYLE_SCALE, you must set a accurate height");
                    mIndicator.setHeaderHeight(child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
                }
            } else if (mFooterView != null && child == mFooterView.getView()) {
                if (isDisabledLoadMore() || isEnabledHideFooterView())
                    continue;
                if (mFooterView.getStyle() == IRefreshView.STYLE_DEFAULT) {
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                } else {
                    final int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
                            paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin, lp.width);
                    final int heightSpec;
                    if (isMovingFooter()) {
                        heightSpec = makeMeasureSpec(mIndicator.getCurrentPosY()
                                + lp.topMargin + lp.bottomMargin, MeasureSpec.EXACTLY);
                    } else {
                        heightSpec = makeMeasureSpec(0, MeasureSpec.EXACTLY);
                    }
                    child.measure(childWidthMeasureSpec, heightSpec);
                }
                if (mFooterView.getCustomHeight() > 0) {
                    mIndicator.setFooterHeight(mFooterView.getCustomHeight());
                } else {
                    if (mFooterView.getStyle() == IRefreshView.STYLE_SCALE)
                        throw new IllegalArgumentException("If footer view's type is " +
                                "STYLE_SCALE, you must set a accurate height");
                    mIndicator.setFooterHeight(child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
                }
            } else {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            }
            maxWidth = Math.max(maxWidth,
                    child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
            maxHeight = Math.max(maxHeight,
                    child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
            childState = combineMeasuredStates(childState, child.getMeasuredState());
        }
        maxWidth += paddingLeft + paddingRight;
        maxHeight += getPaddingTop() + getPaddingBottom();
        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());
        setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, childState),
                resolveSizeAndState(maxHeight, heightMeasureSpec,
                        childState << MEASURED_HEIGHT_STATE_SHIFT));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int count = getChildCount();
        if (count == 0)
            return;
        checkViewsZAxisNeedReset();
        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();
        final int parentRight = r - l - getPaddingRight();
        final int parentBottom = b - t - getPaddingBottom();
        int offsetHeaderY = 0;
        int offsetFooterY = 0;
        if (isMovingHeader()) {
            offsetHeaderY = mIndicator.getCurrentPosY();
        } else if (isMovingFooter()) {
            offsetFooterY = mIndicator.getCurrentPosY();
        }
        int contentBottom = 0;
        boolean pin = (mLoadMoreScrollTargetView != null && !isMovingHeader())
                || isEnabledPinContentView();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE)
                continue;
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (mHeaderView != null && child == mHeaderView.getView()) {
                if (isDisabledRefresh() || isEnabledHideHeaderView()) {
                    child.layout(0, 0, 0, 0);
                    if (sDebug) {
                        SRLog.d(TAG, "onLayout(): header: %s %s %s %s", 0, 0, 0, 0);
                    }
                    continue;
                }
                final int left = paddingLeft + lp.leftMargin;
                final int offset = (isEnabledHeaderDrawerStyle()
                        || mHeaderView.getStyle() == IRefreshView.STYLE_SCALE
                        ? 0 : offsetHeaderY - mIndicator.getHeaderHeight());
                final int top = paddingTop + lp.topMargin + offset;
                final int right = left + child.getMeasuredWidth();
                final int bottom = top + child.getMeasuredHeight();
                child.layout(left, top, right, bottom);
                if (sDebug) {
                    SRLog.d(TAG, "onLayout(): header: %s %s %s %s", left, top, right, bottom);
                }
            } else if (mTargetView != null && child == mTargetView
                    || (mPreviousState != -1 && mChangeStateAnimator != null
                    && mChangeStateAnimator.isRunning() && getView(mPreviousState) == child)) {
                final int left = paddingLeft + lp.leftMargin;
                final int right = left + child.getMeasuredWidth();
                int top, bottom;
                if (isMovingHeader()) {
                    top = paddingTop + lp.topMargin + (pin ? 0 : offsetHeaderY);
                    bottom = top + child.getMeasuredHeight();
                    child.layout(left, top, right, bottom);
                } else if (isMovingFooter()) {
                    top = paddingTop + lp.topMargin - (pin ? 0 : offsetFooterY);
                    bottom = top + child.getMeasuredHeight();
                    child.layout(left, top, right, bottom);
                } else {
                    top = paddingTop + lp.topMargin;
                    bottom = top + child.getMeasuredHeight();
                    child.layout(left, top, right, bottom);
                }
                if (sDebug) {
                    SRLog.d(TAG, "onLayout(): content: %s %s %s %s", left, top, right, bottom);
                }
                if (isEnabledFooterDrawerStyle())
                    contentBottom = paddingTop + lp.topMargin + child.getMeasuredHeight();
                else
                    contentBottom = bottom + lp.bottomMargin;
            } else if (mFooterView == null || mFooterView.getView() != child) {
                layoutOtherViewUseGravity(lp, child, parentRight, parentBottom);
            }
        }
        if (mFooterView != null && mFooterView.getView().getVisibility() != GONE) {
            View child = mFooterView.getView();
            if (isDisabledLoadMore() || isEnabledHideFooterView()) {
                child.layout(0, 0, 0, 0);
                if (sDebug) {
                    SRLog.d(TAG, "onLayout(): footer: %s %s %s %s", 0, 0, 0, 0);
                }
            } else {
                MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                final int left = paddingLeft + lp.leftMargin;
                final int offset = (isEnabledFooterDrawerStyle()
                        ? -mIndicator.getFooterHeight()
                        : -(pin ? offsetFooterY : 0));
                final int top = lp.topMargin + contentBottom + offset;
                final int right = left + child.getMeasuredWidth();
                final int bottom = top + child.getMeasuredHeight();
                child.layout(left, top, right, bottom);
                if (sDebug) {
                    SRLog.d(TAG, "onLayout(): footer: %s %s %s %s", left, top, right, bottom);
                }
            }
        }
        tryToPerformAutoRefresh();
    }

    private void layoutOtherViewUseGravity(LayoutParams lp, View child, int parentRight, int parentBottom) {
        final int width = child.getMeasuredWidth();
        final int height = child.getMeasuredHeight();
        int childLeft, childTop;
        int gravity = lp.mGravity;
        final int layoutDirection = ViewCompat.getLayoutDirection(this);
        final int absoluteGravity = GravityCompat.getAbsoluteGravity(gravity, layoutDirection);
        final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;
        switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
            case Gravity.CENTER_HORIZONTAL:
                childLeft = getPaddingLeft() + (parentRight - getPaddingLeft() - width) / 2
                        + lp.leftMargin - lp.rightMargin;
                break;
            case Gravity.RIGHT:
                childLeft = parentRight - width - lp.rightMargin;
                break;
            default:
                childLeft = getPaddingLeft() + lp.leftMargin;
        }
        switch (verticalGravity) {
            case Gravity.CENTER_VERTICAL:
                childTop = getPaddingTop() + (parentBottom - getPaddingTop() - height) / 2
                        + lp.topMargin - lp.bottomMargin;
                break;
            case Gravity.BOTTOM:
                childTop = parentBottom - height - lp.bottomMargin;
                break;
            default:
                childTop = getPaddingTop() + lp.topMargin;
        }
        child.layout(childLeft, childTop, childLeft + width, childTop + height);
        if (sDebug) {
            SRLog.d(TAG, "onLayout(): child: %s %s %s %s", childLeft, childTop, childLeft
                    + width, childTop + height);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        ensureTargetView();
        if (mState != STATE_CONTENT) {
            ensureContentView();
            if (mContentView != null)
                mContentView.setVisibility(GONE);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!isEnabled() || mTargetView == null) {
            return super.dispatchTouchEvent(ev);
        }
        if ((isEnabledPinRefreshViewWhileLoading() && ((isRefreshing() && isMovingHeader())
                || (isLoadingMore() && isMovingFooter())))
                || mNestedScrollInProgress || (isDisabledLoadMore() && isDisabledRefresh())) {
            return super.dispatchTouchEvent(ev);
        }
        mGestureDetector.onTouchEvent(ev);
        return processDispatchTouchEvent(ev);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean b) {
        if (!((android.os.Build.VERSION.SDK_INT < 21 && mTargetView instanceof AbsListView)
                || (mTargetView != null && !ViewCompat.isNestedScrollingEnabled(mTargetView)))) {
            super.requestDisallowInterceptTouchEvent(b);
        }
    }

    /**
     * Set loadMore scroll target view<br/>
     * For example the content view is a FrameLayout,with a listView in it.<br/>
     * You can call this method,set the listView as load more scroll target view.<br/>
     * Load more compat will try to make it smooth scrolling<br/>
     * <p>
     * 设置加载更多时需要做滑动处理的视图。<br/>
     * 例如在SmoothRefreshLayout中有一个CoordinatorLayout,
     * CoordinatorLayout中有AppbarLayout、RecyclerView等，加载更多时希望被移动的视图为RecyclerVieW
     * 而不是CoordinatorLayout,那么设置RecyclerView为TargetView即可
     *
     * @param view Target view
     */
    @SuppressWarnings({"unused"})
    public void setLoadMoreScrollTargetView(View view) {
        mLoadMoreScrollTargetView = view;
    }

    /**
     * Set the listener to be notified when a refresh is triggered.<br/>
     * <p>
     * 设置刷新监听回调
     *
     * @param listener Listener
     */
    public <T extends OnRefreshListener> void setOnRefreshListener(T listener) {
        mRefreshListener = listener;
    }

    /**
     * Set the listener to be notified when the state changed<br/>
     * <p>
     * 设置状态改变回调
     *
     * @param listener Listener
     */
    @SuppressWarnings({"unused"})
    public void setOnStateChangedListener(OnStateChangedListener listener) {
        mStateChangedListener = listener;
    }

    /**
     * Add a listener to listen the views position change event<br/>
     * <p>
     * 设置UI位置变化回调
     *
     * @param listener Listener
     */
    public void addOnUIPositionChangedListener(OnUIPositionChangedListener listener) {
        if (mUIPositionChangedListeners == null)
            mUIPositionChangedListeners = new ArrayList<>();
        mUIPositionChangedListeners.add(listener);
    }

    /**
     * remove the listener<br/>
     * <p>
     * 移除UI位置变化回调
     *
     * @param listener Listener
     */
    public void removeOnUIPositionChangedListener(OnUIPositionChangedListener listener) {
        if (mUIPositionChangedListeners != null && !mUIPositionChangedListeners.isEmpty())
            mUIPositionChangedListeners.remove(listener);
    }

    @SuppressWarnings({"unused"})
    public void clearOnUIPositionChangedListeners() {
        if (mUIPositionChangedListeners != null)
            mUIPositionChangedListeners.clear();
    }

    /**
     * Set a scrolling callback when loading more.<br/>
     * <p>
     * 设置当加载更多时滚动回调，可使用该属性对内部视图做滑动处理。例如内部视图是ListView，完成加载更多时，
     * 需要将加载出的数据显示出来，那么设置该回调，每次Footer
     * 回滚时拿到滚动的数值对ListView做向上滚动处理，将数据展示处理
     *
     * @param callback Callback that should be called when scrolling on loading more.
     */
    public void setOnLoadMoreScrollCallback(OnLoadMoreScrollCallback callback) {
        mLoadMoreScrollCallback = callback;
    }

    /**
     * Set a callback to override {@link SmoothRefreshLayout#canChildScrollUp()} method. Non-null
     * callback will return the value provided by the callback and ignore all internal logic.<br/>
     * <p>
     * 设置{@link SmoothRefreshLayout#canChildScrollUp()}的重载回调，用来检测内容视图是否在顶部
     *
     * @param callback Callback that should be called when canChildScrollUp() is called.
     */
    public void setOnChildScrollUpCallback(OnChildScrollUpCallback callback) {
        mScrollUpCallback = callback;
    }

    /**
     * Set a callback to override {@link SmoothRefreshLayout#canChildScrollDown()} method. Non-null
     * callback will return the value provided by the callback and ignore all internal logic.<br/>
     * <p>
     * 设置{@link SmoothRefreshLayout#canChildScrollDown()}的重载回调，用来检测内容视图是否在底部
     *
     * @param callback Callback that should be called when canChildScrollDown() is called.
     */
    public void setOnChildScrollDownCallback(OnChildScrollDownCallback callback) {
        mScrollDownCallback = callback;
    }

    /**
     * Set a callback to make sure you need to customize the specified trigger the auto load more
     * rule <br/>
     * <p>
     * 设置自动加载更多的触发条件回调，可自定义具体的触发自动加载更多的条件
     *
     * @param callBack Customize the specified triggered rule
     */
    @SuppressWarnings({"unused"})
    public void setOnPerformAutoLoadMoreCallBack(OnPerformAutoLoadMoreCallBack callBack) {
        mAutoLoadMoreCallBack = callBack;
    }

    /**
     * Set a hook callback when the refresh complete event be triggered. Only can be called on
     * refreshing<br/>
     * <p>
     * 设置一个头部视图刷新完成前的Hook回调
     *
     * @param callback Callback that should be called when refreshComplete() is called.
     */
    public void setOnHookHeaderRefreshCompleteCallback(OnHookUIRefreshCompleteCallBack callback) {
        if (mHeaderRefreshCompleteHook == null)
            mHeaderRefreshCompleteHook = new RefreshCompleteHook();
        mHeaderRefreshCompleteHook.mLayout = this;
        mHeaderRefreshCompleteHook.setHookCallBack(callback);
    }

    /**
     * Set a hook callback when the refresh complete event be triggered. Only can be called on
     * loading more<br/>
     * <p>
     * 设置一个尾部视图刷新完成前的Hook回调
     *
     * @param callback Callback that should be called when refreshComplete() is called.
     */
    @SuppressWarnings({"unused"})
    public void setOnHookFooterRefreshCompleteCallback(OnHookUIRefreshCompleteCallBack callback) {
        if (mFooterRefreshCompleteHook == null)
            mFooterRefreshCompleteHook = new RefreshCompleteHook();
        mFooterRefreshCompleteHook.mLayout = this;
        mFooterRefreshCompleteHook.setHookCallBack(callback);
    }

    /**
     * Set a callback to override
     * {@link SmoothRefreshLayout#isFingerInsideHorizontalView(float, float)}} method.
     * Non-null callback will return the value provided by the callback and ignore all internal
     * logic.<br/>
     * <p>
     * 设置{@link SmoothRefreshLayout#isFingerInsideHorizontalView(float, float)}的重载回调，
     * 用来检查手指按下的点是否位于水平视图内部
     *
     * @param callback Callback that should be called when isFingerInsideHorizontalView(float,
     *                 float) is called。
     */
    public void setOnFingerInsideHorViewCallback(OnFingerInsideHorViewCallback callback) {
        mFingerInsideHorizontalViewCallback = callback;
    }

    public boolean equalsOnHookHeaderRefreshCompleteCallback(OnHookUIRefreshCompleteCallBack callBack) {
        return mHeaderRefreshCompleteHook != null && mHeaderRefreshCompleteHook.mCallBack == callBack;
    }

    @SuppressWarnings({"unused"})
    public boolean equalsOnHookFooterRefreshCompleteCallback(OnHookUIRefreshCompleteCallBack callBack) {
        return mFooterRefreshCompleteHook != null && mFooterRefreshCompleteHook.mCallBack == callBack;
    }

    /**
     * Whether it is being refreshed<br/>
     * <p>
     * 是否在刷新中
     *
     * @return Refreshing
     */
    public boolean isRefreshing() {
        return mStatus == SR_STATUS_REFRESHING;
    }

    /**
     * Whether it is being refreshed<br/>
     * <p>
     * 是否在加载更多种
     *
     * @return Loading
     */
    public boolean isLoadingMore() {
        return mStatus == SR_STATUS_LOADING_MORE;
    }

    /**
     * Whether it is in start position<br/>
     * <p>
     * 是否在起始位置
     *
     * @return Is
     */
    public boolean isInStartPosition() {
        return mIndicator.isInStartPosition();
    }

    /**
     * Whether it is refresh successful<br/>
     * <p>
     * 是否刷新成功
     *
     * @return Is
     */
    public boolean isRefreshSuccessful() {
        return mIsLastRefreshSuccessful;
    }

    /**
     * Perform refresh complete, to reset the state to {@link SmoothRefreshLayout#SR_STATUS_INIT},
     * and set the last refresh operation successfully<br/>
     * <p>
     * 完成刷新，刷新状态为成功
     */
    final public void refreshComplete() {
        refreshComplete(true);
    }

    /**
     * Perform refresh complete, to reset the state to
     * {@link SmoothRefreshLayout#SR_STATUS_INIT}<br/>
     * <p>
     * 完成刷新，刷新状态`isSuccessful`
     *
     * @param isSuccessful Set the last refresh operation status
     */
    final public void refreshComplete(boolean isSuccessful) {
        refreshComplete(isSuccessful, 0);
    }

    /**
     * Perform refresh complete, delay to reset the state to
     * {@link SmoothRefreshLayout#SR_STATUS_INIT} and set the last refresh operation
     * successfully<br/>
     * <p>
     * 完成刷新，延迟`delayDurationToChangeState`时间
     *
     * @param delayDurationToChangeState Delay to change the state to
     *                                   {@link SmoothRefreshLayout#SR_STATUS_COMPLETE}
     */
    final public void refreshComplete(long delayDurationToChangeState) {
        refreshComplete(true, delayDurationToChangeState);
    }

    /**
     * Perform refresh complete, delay to reset the state to
     * {@link SmoothRefreshLayout#SR_STATUS_INIT} and set the last refresh operation<br/>
     * <p>
     * 完成刷新，刷新状态`isSuccessful`，延迟`delayDurationToChangeState`时间
     *
     * @param delayDurationToChangeState Delay to change the state to
     *                                   {@link SmoothRefreshLayout#SR_STATUS_INIT}
     * @param isSuccessful               Set the last refresh operation
     */
    final public void refreshComplete(boolean isSuccessful, long delayDurationToChangeState) {
        if (sDebug) {
            SRLog.d(TAG, "refreshComplete(): isSuccessful: %s", isSuccessful);
        }
        mIsLastRefreshSuccessful = isSuccessful;
        if (!isRefreshing() && !isLoadingMore())
            return;
        if (delayDurationToChangeState <= 0) {
            long delay = mLoadingMinTime - (SystemClock.uptimeMillis() - mLoadingStartTime);
            if (delay <= 0) {
                performRefreshComplete(true);
            } else {
                if (mDelayToRefreshComplete == null)
                    mDelayToRefreshComplete = new DelayToRefreshComplete(this);
                else
                    mDelayToRefreshComplete.mLayoutWeakRf = new WeakReference<>(this);
                postDelayed(mDelayToRefreshComplete, delay);
            }
        } else {
            if (isRefreshing() && mHeaderView != null) {
                mHeaderView.onRefreshComplete(this, isSuccessful);
                mNeedNotifyRefreshComplete = false;
            } else if (isLoadingMore() && mFooterView != null) {
                mFooterView.onRefreshComplete(this, isSuccessful);
                mNeedNotifyRefreshComplete = false;
            }
            mDelayedRefreshComplete = true;
            long delay = mLoadingMinTime - (SystemClock.uptimeMillis() - mLoadingStartTime);
            if (delayDurationToChangeState < delay)
                delayDurationToChangeState = delay;
            if (mDelayToRefreshComplete == null)
                mDelayToRefreshComplete = new DelayToRefreshComplete(this);
            else
                mDelayToRefreshComplete.mLayoutWeakRf = new WeakReference<>(this);
            postDelayed(mDelayToRefreshComplete, delayDurationToChangeState);
        }
    }

    /**
     * Set the loading min time<br/>
     * <p>
     * 设置加载过程的最小持续时间
     *
     * @param time Millis
     */
    @SuppressWarnings({"unused"})
    public void setLoadingMinTime(long time) {
        mLoadingMinTime = time;
    }

    /**
     * Get the header height,
     * After the measurement is completed, the height will have value<br/>
     * <p>
     * 获取Header的高度，在布局计算完成前无法得到准确的值
     *
     * @return Height default is -1
     */
    @SuppressWarnings({"unused"})
    public int getHeaderHeight() {
        return mIndicator.getHeaderHeight();
    }

    /**
     * Get the footer height,
     * After the measurement is completed, the height will have value<br/>
     * <p>
     * 获取Footer的高度，在布局计算完成前无法得到准确的值
     *
     * @return Height default is -1
     */
    @SuppressWarnings({"unused"})
    public int getFooterHeight() {
        return mIndicator.getFooterHeight();
    }

    /**
     * Perform auto refresh at once<br/>
     * <p>
     * 自动刷新并立即触发刷新回调
     */
    public void autoRefresh() {
        autoRefresh(true);
    }

    /**
     * If @param atOnce has been set to true. Auto perform refresh at once.<br/>
     * <p>
     * 自动刷新，`atOnce`立即触发刷新回调
     *
     * @param atOnce Auto refresh at once
     */
    public void autoRefresh(boolean atOnce) {
        autoRefresh(atOnce, true);
    }

    /**
     * If @param atOnce has been set to true. Auto perform refresh at once.
     * If @param smooth has been set to true. Auto perform refresh will using smooth scrolling.<br/>
     * <p>
     * 自动刷新，`atOnce`立即触发刷新回调，`smooth`滚动到触发位置
     *
     * @param atOnce       Auto refresh at once
     * @param smoothScroll Auto refresh use smooth scrolling
     */
    public void autoRefresh(boolean atOnce, boolean smoothScroll) {
        if (mStatus != SR_STATUS_INIT) {
            return;
        }
        if (sDebug) {
            SRLog.d(TAG, "autoRefresh(): atOnce: %s, smoothScroll: %s", atOnce, smoothScroll);
        }
        mFlag |= atOnce ? FLAG_AUTO_REFRESH_AT_ONCE : FLAG_AUTO_REFRESH_BUT_LATER;
        mStatus = SR_STATUS_PREPARE;
        if (mHeaderView != null)
            mHeaderView.onRefreshPrepare(this);
        mIndicator.setMovingStatus(IIndicator.MOVING_HEADER);
        mViewStatus = SR_VIEW_STATUS_HEADER_IN_PROCESSING;
        mAutomaticActionUseSmoothScroll = smoothScroll;
        int offsetToRefresh = mIndicator.getOffsetToRefresh();
        if (offsetToRefresh <= 0) {
            mAutomaticActionInScrolling = false;
            mAutomaticActionTriggered = false;
        } else {
            mAutomaticActionTriggered = true;
            mScrollChecker.tryToScrollTo(offsetToRefresh, smoothScroll ? mDurationToCloseHeader : 0);
            mAutomaticActionInScrolling = smoothScroll;
        }
        if (atOnce) {
            triggeredRefresh();
        }
    }

    /**
     * Perform auto load more at once<br/>
     * <p>
     * 自动加载更多，并立即触发刷新回调
     */
    @SuppressWarnings({"unused"})
    public void autoLoadMore() {
        autoLoadMore(true);
    }

    /**
     * If @param atOnce has been set to true. Auto perform load more at once.<br/>
     * <p>
     * 自动加载更多，`atOnce`立即触发刷新回调
     *
     * @param atOnce Auto load more at once
     */
    public void autoLoadMore(boolean atOnce) {
        autoLoadMore(atOnce, true);
    }

    /**
     * If @param atOnce has been set to true. Auto perform load more at once.
     * If @param smooth has been set to true. Auto perform load more will using smooth scrolling
     * .<br/>
     * <p>
     * 自动加载更多，`atOnce`立即触发刷新回调，`smooth`滚动到触发位置
     *
     * @param atOnce       Auto load more at once
     * @param smoothScroll Auto load more use smooth scrolling
     */
    public void autoLoadMore(boolean atOnce, boolean smoothScroll) {
        if (mStatus != SR_STATUS_INIT) {
            return;
        }
        if (sDebug) {
            SRLog.d(TAG, "autoLoadMore(): atOnce: %s, smoothScroll: %s", atOnce, smoothScroll);
        }
        mFlag |= atOnce ? FLAG_AUTO_REFRESH_AT_ONCE : FLAG_AUTO_REFRESH_BUT_LATER;
        mStatus = SR_STATUS_PREPARE;
        if (mFooterView != null)
            mFooterView.onRefreshPrepare(this);
        mIndicator.setMovingStatus(IIndicator.MOVING_FOOTER);
        mViewStatus = SR_VIEW_STATUS_FOOTER_IN_PROCESSING;
        mAutomaticActionUseSmoothScroll = smoothScroll;
        int offsetToLoadMore = mIndicator.getOffsetToLoadMore();
        if (offsetToLoadMore <= 0) {
            mAutomaticActionInScrolling = false;
            mAutomaticActionTriggered = false;
        } else {
            mAutomaticActionTriggered = true;
            mScrollChecker.tryToScrollTo(offsetToLoadMore, smoothScroll ? mDurationToCloseFooter : 0);
            mAutomaticActionInScrolling = smoothScroll;
        }
        if (atOnce) {
            triggeredLoadMore();
        }
    }

    /**
     * The resistance while you are moving<br/>
     * <p>
     * 移动刷新视图时候的移动阻尼
     *
     * @param resistance Resistance
     */
    @SuppressWarnings({"unused"})
    public void setResistance(@FloatRange(from = 0, to = Float.MAX_VALUE) float resistance) {
        mIndicator.setResistance(resistance);
    }

    /**
     * The resistance while you are moving footer<br/>
     * <p>
     * 移动Footer视图时候的移动阻尼
     *
     * @param resistance Resistance
     */
    @SuppressWarnings({"unused"})
    public void setResistanceOfFooter(@FloatRange(from = 0, to = Float.MAX_VALUE) float resistance) {
        mIndicator.setResistanceOfFooter(resistance);
    }

    /**
     * The resistance while you are moving header<br/>
     * <p>
     * 移动Header视图时候的移动阻尼
     *
     * @param resistance Resistance
     */
    @SuppressWarnings({"unused"})
    public void setResistanceOfHeader(@FloatRange(from = 0, to = Float.MAX_VALUE) float resistance) {
        mIndicator.setResistanceOfHeader(resistance);
    }

    /**
     * the height ratio of the trigger refresh<br/>
     * <p>
     * 设置触发刷新时的位置占刷新视图的高度比
     *
     * @param ratio Height ratio
     */
    @SuppressWarnings({"unused"})
    public void setRatioOfRefreshViewHeightToRefresh(@FloatRange(from = 0, to = Float.MAX_VALUE) float ratio) {
        mIndicator.setRatioOfRefreshViewHeightToRefresh(ratio);
    }

    @SuppressWarnings({"unused"})
    public float getRatioOfHeaderHeightToRefresh() {
        return mIndicator.getRatioOfHeaderHeightToRefresh();
    }

    /**
     * the height ratio of the trigger refresh<br/>
     * <p>
     * 设置触发下拉刷新时的位置占Header视图的高度比
     *
     * @param ratio Height ratio
     */
    @SuppressWarnings({"unused"})
    public void setRatioOfHeaderHeightToRefresh(@FloatRange(from = 0, to = Float.MAX_VALUE) float ratio) {
        mIndicator.setRatioOfHeaderHeightToRefresh(ratio);
    }

    @SuppressWarnings({"unused"})
    public float getRatioOfFooterHeightToRefresh() {
        return mIndicator.getRatioOfFooterHeightToRefresh();
    }

    /**
     * The height ratio of the trigger refresh<br/>
     * <p>
     * 设置触发加载更多时的位置占Footer视图的高度比
     *
     * @param ratio Height ratio
     */
    @SuppressWarnings({"unused"})
    public void setRatioOfFooterHeightToRefresh(@FloatRange(from = 0, to = Float.MAX_VALUE) float ratio) {
        mIndicator.setRatioOfFooterHeightToRefresh(ratio);
    }

    /**
     * Set in the refresh to keep the refresh view's position of the ratio of the view's height<br/>
     * <p>
     * 刷新中保持视图位置占刷新视图的高度比（默认:`1f`）,该属性的值必须小于等于触发刷新高度比才会有效果，
     * 当开启了{@link SmoothRefreshLayout#isEnabledKeepRefreshView}后，该属性会生效
     *
     * @param ratio Height ratio
     */
    public void setOffsetRatioToKeepRefreshViewWhileLoading(@FloatRange(from = 0, to = Float.MAX_VALUE) float ratio) {
        mIndicator.setOffsetRatioToKeepHeaderWhileLoading(ratio);
        mIndicator.setOffsetRatioToKeepFooterWhileLoading(ratio);
    }

    /**
     * Set in the refresh to keep the header view position of the ratio of the view's height<br/>
     * <p>
     * 刷新中保持视图位置占Header视图的高度比（默认:`1f`）,该属性的值必须小于等于触发刷新高度比才会有效果
     *
     * @param ratio Height ratio
     */
    public void setOffsetRatioToKeepHeaderWhileLoading(@FloatRange(from = 0, to = Float.MAX_VALUE) float ratio) {
        mIndicator.setOffsetRatioToKeepHeaderWhileLoading(ratio);
    }

    /**
     * Set in the refresh to keep the footer view position of the ratio of the view's height<br/>
     * <p>
     * 刷新中保持视图位置占Header视图的高度比（默认:`1f`）,该属性的值必须小于等于触发刷新高度比才会有效果
     *
     * @param ratio Height ratio
     */
    @SuppressWarnings({"unused"})
    public void setOffsetRatioToKeepFooterWhileLoading(@FloatRange(from = 0, to = Float.MAX_VALUE) float ratio) {
        mIndicator.setOffsetRatioToKeepFooterWhileLoading(ratio);
    }

    /**
     * Set the duration ratio for Cross-Boundary-Rebound(OverScroll)<br/>
     * <p>
     * 设置越界回弹时间比例（默认:`0.5f`）
     *
     * @param ratio Ratio
     */
    @SuppressWarnings({"unused"})
    public void setOverScrollDurationRatio(@FloatRange(from = 0, to = Float.MAX_VALUE) float ratio) {
        mOverScrollDurationRatio = ratio;
    }

    /**
     * Set the max duration for Cross-Boundary-Rebound(OverScroll)<br/>
     * <p>
     * 设置越界回弹效果的最大持续时长（默认:`500`）
     *
     * @param duration Duration
     */
    @SuppressWarnings({"unused"})
    public void setMaxOverScrollDuration(@IntRange(from = 0, to = Integer.MAX_VALUE) int duration) {
        mMaxOverScrollDuration = duration;
    }

    /**
     * Set the min duration for Cross-Boundary-Rebound(OverScroll)<br/>
     * <p>
     * 设置越界回弹效果的最小持续时长（默认:`150`）
     *
     * @param duration Duration
     */
    @SuppressWarnings({"unused"})
    public void setMinOverScrollDuration(@IntRange(from = 0, to = Integer.MAX_VALUE) int duration) {
        mMinOverScrollDuration = duration;
    }

    /**
     * The duration of return back to the start position <br/>
     * <p>
     * 设置刷新完成回滚到起始位置的时间
     *
     * @param duration Millis
     */
    @SuppressWarnings({"unused"})
    public void setDurationToClose(@IntRange(from = 0, to = Integer.MAX_VALUE) int duration) {
        mDurationToCloseHeader = duration;
        mDurationToCloseFooter = duration;
    }

    /**
     * Get the  duration of header return back to the start position
     *
     * @return mDuration
     */
    @SuppressWarnings({"unused"})
    public int getDurationToCloseHeader() {
        return mDurationToCloseHeader;
    }

    /**
     * The duration of header return back to the start position<br/>
     * <p>
     * 设置Header刷新完成回滚到起始位置的时间
     *
     * @param duration Millis
     */
    @SuppressWarnings({"unused"})
    public void setDurationToCloseHeader(@IntRange(from = 0, to = Integer.MAX_VALUE) int duration) {
        mDurationToCloseHeader = duration;
    }

    /**
     * Get the  duration of footer return back to the start position<br/>
     * <p>
     * 设置Footer刷新完成回滚到起始位置的时间
     *
     * @return mDuration
     */
    @SuppressWarnings({"unused"})
    public int getDurationToCloseFooter() {
        return mDurationToCloseFooter;
    }

    /**
     * The duration of footer return back to the start position
     *
     * @param duration Millis
     */
    @SuppressWarnings({"unused"})
    public void setDurationToCloseFooter(@IntRange(from = 0, to = Integer.MAX_VALUE) int duration) {
        mDurationToCloseFooter = duration;
    }

    /**
     * The duration of refresh view to return back to the keep refresh view position<br/>
     * <p>
     * 设置回滚到保持刷新视图位置的时间
     *
     * @param duration Millis
     */
    @SuppressWarnings({"unused"})
    public void setDurationOfBackToKeepRefreshViewPosition(@IntRange(from = 0, to = Integer.MAX_VALUE) int duration) {
        mDurationOfBackToHeaderHeight = duration;
        mDurationOfBackToFooterHeight = duration;
    }

    /**
     * Get the duration of header return back to the keep header position<br/>
     * <p>
     * 得到回滚到保持Header视图位置的时间
     *
     * @return Duration
     */
    @SuppressWarnings({"unused"})
    public int getDurationOfBackToKeepHeaderPosition() {
        return mDurationOfBackToHeaderHeight;
    }

    /**
     * The duration of header return back to the keep header position<br/>
     * <p>
     * 设置回滚到保持Header视图位置的时间
     *
     * @param duration Millis
     */
    @SuppressWarnings({"unused"})
    public void setDurationOfBackToKeepHeaderPosition(@IntRange(from = 0, to = Integer.MAX_VALUE) int duration) {
        this.mDurationOfBackToHeaderHeight = duration;
    }

    /**
     * Get the duration of footer return back to the keep footer position<br/>
     * <p>
     * 得到回滚到保持Footer视图位置的时间
     *
     * @return mDuration
     */
    @SuppressWarnings({"unused"})
    public int getDurationOfBackToKeepFooterPosition() {
        return mDurationOfBackToFooterHeight;
    }

    /**
     * The duration of footer return back to the keep footer position<br/>
     * <p>
     * 设置回顾到保持Footer视图位置的时间
     *
     * @param duration Millis
     */
    @SuppressWarnings({"unused"})
    public void setDurationOfBackToKeepFooterPosition(@IntRange(from = 0, to = Integer.MAX_VALUE) int duration) {
        this.mDurationOfBackToFooterHeight = duration;
    }

    /**
     * The max ratio of height for the refresh view when the finger moves<br/>
     * <p>
     * 设置最大移动距离占刷新视图的高度比
     *
     * @param ratio The max ratio of refresh view
     */
    @SuppressWarnings({"unused"})
    public void setCanMoveTheMaxRatioOfRefreshViewHeight(@FloatRange(from = 0, to = Float.MAX_VALUE) float ratio) {
        mIndicator.setCanMoveTheMaxRatioOfRefreshViewHeight(ratio);
    }

    @SuppressWarnings({"unused"})
    public float getCanMoveTheMaxRatioOfHeaderHeight() {
        return mIndicator.getCanMoveTheMaxRatioOfHeaderHeight();
    }

    /**
     * The max ratio of height for the header view when the finger moves<br/>
     * <p>
     * 最大移动距离占Header视图的高度比
     *
     * @param ratio The max ratio of header view
     */
    @SuppressWarnings({"unused"})
    public void setCanMoveTheMaxRatioOfHeaderHeight(@FloatRange(from = 0, to = Float.MAX_VALUE) float ratio) {
        mIndicator.setCanMoveTheMaxRatioOfHeaderHeight(ratio);
    }

    @SuppressWarnings({"unused"})
    public float getCanMoveTheMaxRatioOfFooterHeight() {
        return mIndicator.getCanMoveTheMaxRatioOfFooterHeight();
    }

    /**
     * The max ratio of height for the footer view when the finger moves<br/>
     * <p>
     * 最大移动距离占Footer视图的高度比
     *
     * @param ratio The max ratio of footer view
     */
    @SuppressWarnings({"unused"})
    public void setCanMoveTheMaxRatioOfFooterHeight(@FloatRange(from = 0, to = Float.MAX_VALUE) float ratio) {
        mIndicator.setCanMoveTheMaxRatioOfFooterHeight(ratio);
    }

    /**
     * The flag has set to autoRefresh<br/>
     * <p>
     * 是否处于自动刷新刷新
     *
     * @return Enabled
     */
    public boolean isAutoRefresh() {
        return (mFlag & MASK_AUTO_REFRESH) > 0;
    }

    /**
     * If enable has been set to true. The user can perform next PTR at once.<br/>
     * <p>
     * 是否已经开启完成刷新后即可立即触发刷新
     *
     * @return Is enable
     */
    public boolean isEnabledNextPtrAtOnce() {
        return (mFlag & FLAG_ENABLE_NEXT_AT_ONCE) > 0;
    }

    /**
     * If @param enable has been set to true. The user can perform next PTR at once.<br/>
     * <p>
     * 设置开启完成刷新后即可立即触发刷新
     *
     * @param enable Enable
     */
    public void setEnableNextPtrAtOnce(boolean enable) {
        if (enable) {
            mFlag = mFlag | FLAG_ENABLE_NEXT_AT_ONCE;
        } else {
            mFlag = mFlag & ~FLAG_ENABLE_NEXT_AT_ONCE;
        }
    }

    /**
     * The flag has set enabled overScroll<br/>
     * <p>
     * 是否已经开启越界回弹
     *
     * @return Enabled
     */
    public boolean isEnabledOverScroll() {
        return (mFlag & FLAG_ENABLE_OVER_SCROLL) > 0;
    }

    /**
     * If @param enable has been set to true. Will supports over scroll.<br/>
     * <p>
     * 设置开始越界回弹
     *
     * @param enable Enable
     */
    public void setEnableOverScroll(boolean enable) {
        if (enable) {
            mFlag = mFlag | FLAG_ENABLE_OVER_SCROLL;
        } else {
            mFlag = mFlag & ~FLAG_ENABLE_OVER_SCROLL;
        }
    }

    /**
     * The flag has set enabled to intercept the touch event while loading<br/>
     * <p>
     * 是否已经开启刷新中拦截消耗触摸事件
     *
     * @return Enabled
     */
    public boolean isEnabledInterceptEventWhileLoading() {
        return (mFlag & FLAG_ENABLE_INTERCEPT_EVENT_WHILE_LOADING) > 0;
    }

    /**
     * If @param enable has been set to true. Will intercept the touch event while loading<br/>
     * <p>
     * 开启刷新中拦截消耗触摸事件
     *
     * @param enable Enable
     */
    public void setEnabledInterceptEventWhileLoading(boolean enable) {
        if (enable) {
            mFlag = mFlag | FLAG_ENABLE_INTERCEPT_EVENT_WHILE_LOADING;
        } else {
            mFlag = mFlag & ~FLAG_ENABLE_INTERCEPT_EVENT_WHILE_LOADING;
        }
    }

    /**
     * The flag has been set to pull to refresh<br/>
     * <p>
     * 是否已经开启拉动刷新，下拉或者上拉到触发刷新位置即立即触发刷新
     *
     * @return Enabled
     */
    public boolean isEnabledPullToRefresh() {
        return (mFlag & FLAG_ENABLE_PULL_TO_REFRESH) > 0;
    }

    /**
     * If @param enable has been set to true. When the current pos >= refresh offsets, perform
     * refresh<br/>
     * <p>
     * 设置开启拉动刷新,下拉或者上拉到触发刷新位置即立即触发刷新
     *
     * @param enable Pull to refresh
     */
    public void setEnablePullToRefresh(boolean enable) {
        if (enable) {
            mFlag = mFlag | FLAG_ENABLE_PULL_TO_REFRESH;
        } else {
            mFlag = mFlag & ~FLAG_ENABLE_PULL_TO_REFRESH;
        }
    }

    /**
     * The flag has been set to enabled header drawerStyle<br/>
     * <p>
     * 是否已经开启Header的抽屉效果，即Header在Content下面且Header固定在顶部
     *
     * @return Enabled
     */
    public boolean isEnabledHeaderDrawerStyle() {
        return (mFlag & FLAG_ENABLE_HEADER_DRAWER_STYLE) > 0;
    }

    /**
     * The flag has been set to check whether the finger pressed point is inside horizontal
     * view<br/>
     * <p>
     * 是否已经开启检查按下点是否位于水平滚动视图内
     *
     * @return Enabled
     */
    public boolean isEnableCheckFingerInsideHorView() {
        return (mFlag & FLAG_ENABLE_CHECK_FINGER_INSIDE) > 0;
    }

    /**
     * If @param enable has been set to true. Touch event handling will be check whether the finger
     * pressed point is inside horizontal view<br/>
     * <p>
     * 设置开启检查按下点是否位于水平滚动视图内
     *
     * @param enable Pull to refresh
     */
    public void setEnableCheckFingerInsideHorView(boolean enable) {
        if (enable) {
            mFlag = mFlag | FLAG_ENABLE_CHECK_FINGER_INSIDE;
        } else {
            mFlag = mFlag & ~FLAG_ENABLE_CHECK_FINGER_INSIDE;
        }
    }

    /**
     * If @param enable has been set to true.Enable header drawerStyle<br/>
     * <p>
     * 设置开启Header的抽屉效果，即Header在Content下面且Header固定在顶部.
     * 由于该效果需要改变层级关系，所以需要重新布局
     *
     * @param enable enable header drawerStyle
     */
    public void setEnableHeaderDrawerStyle(boolean enable) {
        if (enable) {
            mFlag = mFlag | FLAG_ENABLE_HEADER_DRAWER_STYLE;
        } else {
            mFlag = mFlag & ~FLAG_ENABLE_HEADER_DRAWER_STYLE;
        }
        mViewsZAxisNeedReset = true;
        requestLayout();
    }

    /**
     * The flag has been set to enabled footer drawerStyle<br/>
     * <p>
     * 是否已经开启Footer的抽屉效果，即Footer在Content下面且Footer固定在底部
     *
     * @return Enabled
     */
    public boolean isEnabledFooterDrawerStyle() {
        return (mFlag & FLAG_ENABLE_FOOTER_DRAWER_STYLE) > 0;
    }

    /**
     * If @param enable has been set to true.Enable footer drawerStyle<br/>
     * <p>
     * 设置开启Footer的抽屉效果，即Footer在Content下面且Footer固定在底部.
     * 由于该效果需要改变层级关系，所以需要重新布局
     *
     * @param enable enable footer drawerStyle
     */
    public void setEnableFooterDrawerStyle(boolean enable) {
        if (enable) {
            mFlag = mFlag | FLAG_ENABLE_FOOTER_DRAWER_STYLE;
        } else {
            mFlag = mFlag & ~FLAG_ENABLE_FOOTER_DRAWER_STYLE;
        }
        mViewsZAxisNeedReset = true;
        requestLayout();
    }

    /**
     * The flag has been set to disabled perform refresh<br/>
     * <p>
     * 是否已经关闭触发下拉刷新
     *
     * @return Disabled
     */
    public boolean isDisabledPerformRefresh() {
        return (mFlag & MASK_DISABLE_PERFORM_REFRESH) > 0;
    }

    /**
     * If @param disable has been set to true.Will never perform refresh<br/>
     * <p>
     * 设置是否关闭触发下拉刷新
     *
     * @param disable Disable perform refresh
     */
    public void setDisablePerformRefresh(boolean disable) {
        if (disable) {
            mFlag = mFlag | FLAG_DISABLE_PERFORM_REFRESH;
        } else {
            mFlag = mFlag & ~FLAG_DISABLE_PERFORM_REFRESH;
        }
    }

    /**
     * The flag has been set to disabled refresh<br/>
     * <p>
     * 是否已经关闭刷新
     *
     * @return Disabled
     */
    public boolean isDisabledRefresh() {
        return (mFlag & FLAG_DISABLE_REFRESH) > 0;
    }

    /**
     * If @param disable has been set to true.Will disable refresh<br/>
     * <p>
     * 设置是否关闭刷新
     *
     * @param disable Disable refresh
     */
    public void setDisableRefresh(boolean disable) {
        if (disable) {
            mFlag = mFlag | FLAG_DISABLE_REFRESH;
        } else {
            mFlag = mFlag & ~FLAG_DISABLE_REFRESH;
        }
        requestLayout();
    }

    /**
     * The flag has been set to disabled perform load more<br/>
     * <p>
     * 是否已经关闭触发加载更多
     *
     * @return Disabled
     */
    public boolean isDisabledPerformLoadMore() {
        return (mFlag & MASK_DISABLE_PERFORM_LOAD_MORE) > 0;
    }

    /**
     * If @param disable has been set to true.Will never perform load more<br/>
     * <p>
     * 设置是否关闭触发加载更多
     *
     * @param disable Disable perform load more
     */
    public void setDisablePerformLoadMore(boolean disable) {
        if (disable) {
            mFlag = mFlag | FLAG_DISABLE_PERFORM_LOAD_MORE;
        } else {
            mFlag = mFlag & ~FLAG_DISABLE_PERFORM_LOAD_MORE;
        }
    }

    /**
     * The flag has been set to disabled load more<br/>
     * <p>
     * 是否已经关闭加载更多
     *
     * @return Disabled
     */
    public boolean isDisabledLoadMore() {
        return (mFlag & FLAG_DISABLE_LOAD_MORE) > 0;
    }

    /**
     * If @param disable has been set to true.Will disable load more<br/>
     * <p>
     * 设置关闭加载更多
     *
     * @param disable Disable load more
     */
    public void setDisableLoadMore(boolean disable) {
        if (disable) {
            mFlag = mFlag | FLAG_DISABLE_LOAD_MORE;
        } else {
            mFlag = mFlag & ~FLAG_DISABLE_LOAD_MORE;
        }
        requestLayout();
    }

    /**
     * The flag has been set to hided header view<br/>
     * <p>
     * 是否已经开启不显示Header
     *
     * @return hided
     */
    public boolean isEnabledHideHeaderView() {
        return (mFlag & FLAG_ENABLE_HIDE_HEADER_VIEW) > 0;
    }

    /**
     * If @param enable has been set to true.Will hide the header<br/>
     * <p>
     * 设置是否开启不显示Header
     *
     * @param enable Enable hide the header
     */
    public void setEnableHideHeaderView(boolean enable) {
        if (enable) {
            mFlag = mFlag | FLAG_ENABLE_HIDE_HEADER_VIEW;
        } else {
            mFlag = mFlag & ~FLAG_ENABLE_HIDE_HEADER_VIEW;
        }
        requestLayout();
    }

    /**
     * The flag has been set to hided footer view<br/>
     * <p>
     * 是否已经开启不显示Footer
     *
     * @return hided
     */
    public boolean isEnabledHideFooterView() {
        return (mFlag & FLAG_ENABLE_HIDE_FOOTER_VIEW) > 0;
    }

    /**
     * If @param enable has been set to true.Will hide the footer<br/>
     * <p>
     * 设置是否开启不显示Footer
     *
     * @param enable Enable hide the footer
     */
    public void setEnableHideFooterView(boolean enable) {
        if (enable) {
            mFlag = mFlag | FLAG_ENABLE_HIDE_FOOTER_VIEW;
        } else {
            mFlag = mFlag & ~FLAG_ENABLE_HIDE_FOOTER_VIEW;
        }
        requestLayout();
    }

    /**
     * The flag has been set to disabled when horizontal move<br/>
     * <p>
     * 是否已经设置不响应横向滑动
     *
     * @return Disabled
     */
    public boolean isDisabledWhenHorizontalMove() {
        return (mFlag & FLAG_DISABLE_WHEN_HORIZONTAL_MOVE) > 0;
    }

    /**
     * Set whether to filter the horizontal move<br/>
     * <p>
     * 设置不响应横向滑动，当内部视图含有需要响应横向滑动的子视图时，需要设置该属性，否则自视图无法响应横向滑动
     *
     * @param disable Enable
     */
    public void setDisableWhenHorizontalMove(boolean disable) {
        if (disable) {
            mFlag = mFlag | FLAG_DISABLE_WHEN_HORIZONTAL_MOVE;
        } else {
            mFlag = mFlag & ~FLAG_DISABLE_WHEN_HORIZONTAL_MOVE;
        }
    }

    /**
     * The flag has been set to enabled load more has no more data<br/>
     * <p>
     * 是否已经开启加载更多完成已无更多数据，自定义Footer可根据该属性判断是否显示无更多数据的提示
     *
     * @return Enabled
     */
    public boolean isEnabledLoadMoreNoMoreData() {
        return (mFlag & FLAG_ENABLE_LOAD_MORE_NO_MORE_DATA) > 0;
    }

    /**
     * If @param enable has been set to true. The footer will show no more data and will never
     * trigger load more<br/>
     * <p>
     * 设置开启加载更多完成已无更多数据，当该属性设置为`true`时，将不再触发加载更多。
     *
     * @param enable Enable no more data
     */
    public void setEnableLoadMoreNoMoreData(boolean enable) {
        if (enable) {
            mFlag = mFlag | FLAG_ENABLE_LOAD_MORE_NO_MORE_DATA;
        } else {
            mFlag = mFlag & ~FLAG_ENABLE_LOAD_MORE_NO_MORE_DATA;
        }
    }

    /**
     * The flag has been set to keep refresh view while loading<br/>
     * <p>
     * 是否已经开启保持刷新视图
     *
     * @return Enabled
     */
    public boolean isEnabledKeepRefreshView() {
        return (mFlag & FLAG_ENABLE_KEEP_REFRESH_VIEW) > 0;
    }

    /**
     * If @param enable has been set to true.When the current pos> = keep refresh view pos,
     * it rolls back to the keep refresh view pos to perform refresh and remains until the refresh
     * completed<br/>
     * <p>
     * 开启刷新中保持刷新视图位置
     *
     * @param enable Keep refresh view
     */
    public void setEnableKeepRefreshView(boolean enable) {
        if (enable) {
            mFlag = mFlag | FLAG_ENABLE_KEEP_REFRESH_VIEW;
        } else {
            mFlag = mFlag & ~FLAG_ENABLE_KEEP_REFRESH_VIEW;
            setEnablePinRefreshViewWhileLoading(false);
        }
    }

    /**
     * The flag has been set to perform load more when the content view scrolling to bottom<br/>
     * <p>
     * 是否已经开启到底部自动加载更多
     *
     * @return Enabled
     */
    public boolean isEnabledScrollToBottomAutoLoadMore() {
        return (mFlag & FLAG_ENABLE_WHEN_SCROLLING_TO_BOTTOM_TO_PERFORM_LOAD_MORE) > 0;
    }

    /**
     * If @param enable has been set to true.When the content view scrolling to bottom,
     * It will be perform load more<br/>
     * <p>
     * 开启到底自动加载更多
     *
     * @param enable Enable
     */
    public void setEnableScrollToBottomAutoLoadMore(boolean enable) {
        if (enable) {
            mFlag = mFlag | FLAG_ENABLE_WHEN_SCROLLING_TO_BOTTOM_TO_PERFORM_LOAD_MORE;
        } else {
            mFlag = mFlag & ~FLAG_ENABLE_WHEN_SCROLLING_TO_BOTTOM_TO_PERFORM_LOAD_MORE;
        }
    }

    /**
     * The flag has been set to perform refresh when the content view scrolling to top<br/>
     * <p>
     * 是否已经开启到顶自动刷新
     *
     * @return Enabled
     */
    public boolean isEnabledScrollToTopAutoRefresh() {
        return (mFlag & FLAG_ENABLE_WHEN_SCROLLING_TO_TOP_TO_PERFORM_REFRESH) > 0;
    }

    /**
     * If @param enable has been set to true.When the content view scrolling to top,
     * It will be perform refresh<br/>
     * <p>
     * 开启到顶自动刷新
     *
     * @param enable Enable
     */
    public void setEnableScrollToTopAutoRefresh(boolean enable) {
        if (enable) {
            mFlag = mFlag | FLAG_ENABLE_WHEN_SCROLLING_TO_TOP_TO_PERFORM_REFRESH;
        } else {
            mFlag = mFlag & ~FLAG_ENABLE_WHEN_SCROLLING_TO_TOP_TO_PERFORM_REFRESH;
        }
    }

    /**
     * The flag has been set to pinned refresh view while loading<br/>
     * <p>
     * 是否已经开启刷新过程中固定刷新视图且不响应触摸移动
     *
     * @return Enabled
     */
    public boolean isEnabledPinRefreshViewWhileLoading() {
        return (mFlag & FLAG_ENABLE_PIN_REFRESH_VIEW_WHILE_LOADING) > 0;
    }

    /**
     * If @param enable has been set to true.The refresh view will pinned at the refresh offset
     * position<br/>
     * <p>
     * 设置开启刷新过程中固定刷新视图且不响应触摸移动，该属性只有在{@link SmoothRefreshLayout#setEnablePinContentView(boolean)}
     * 和{@link SmoothRefreshLayout#setEnableKeepRefreshView(boolean)}2个属性都为`true`时才能生效
     *
     * @param enable Pin content view
     */
    public void setEnablePinRefreshViewWhileLoading(boolean enable) {
        if (enable) {
            if (isEnabledPinContentView() && isEnabledKeepRefreshView()) {
                mFlag = mFlag | FLAG_ENABLE_PIN_REFRESH_VIEW_WHILE_LOADING;
            } else {
                throw new IllegalArgumentException("This method can only be enabled if setEnablePinContentView" +
                        " and setEnableKeepRefreshView are set be true");
            }
        } else {
            mFlag = mFlag & ~FLAG_ENABLE_PIN_REFRESH_VIEW_WHILE_LOADING;
        }
    }

    /**
     * The flag has been set to pinned content view while loading<br/>
     * <p>
     * 是否已经开启了固定内容视图
     *
     * @return Enabled
     */
    public boolean isEnabledPinContentView() {
        return (mFlag & FLAG_ENABLE_PIN_CONTENT_VIEW) > 0;
    }

    /**
     * If @param enable has been set to true.The content view will be pinned in the start pos
     * unless overScroll flag has been set and in overScrolling<br/>
     * <p>
     * 设置开启固定内容视图
     *
     * @param enable Pin content view
     */
    public void setEnablePinContentView(boolean enable) {
        if (enable) {
            mFlag = mFlag | FLAG_ENABLE_PIN_CONTENT_VIEW;
        } else {
            mFlag = mFlag & ~FLAG_ENABLE_PIN_CONTENT_VIEW;
            setEnablePinRefreshViewWhileLoading(false);
        }
    }

    /**
     * Set the footer view<br/>
     * <p>
     * 设置Footer
     *
     * @param footer Footer view
     */
    public void setFooterView(@NonNull IRefreshView footer) {
        if (mFooterView != null) {
            removeView(mFooterView.getView());
            mFooterView = null;
        }
        if (footer.getType() != IRefreshView.TYPE_FOOTER)
            throw new IllegalArgumentException("Wrong type,FooterView type must be " +
                    "TYPE_FOOTER");
        View view = footer.getView();
        addFreshViewLayoutParams(view);
        mViewsZAxisNeedReset = true;
        addView(view);
    }

    /**
     * Set the header view<br/>
     * <p>
     * 设置Header
     *
     * @param header Header view
     */
    public void setHeaderView(@NonNull IRefreshView header) {
        if (mHeaderView != null) {
            if (mHeaderView instanceof MaterialHeader) {
                ((MaterialHeader) mHeaderView).release();
            }
            removeView(mHeaderView.getView());
            mHeaderView = null;
        }
        if (header.getType() != IRefreshView.TYPE_HEADER)
            throw new IllegalArgumentException("Wrong type,HeaderView type must be " +
                    "TYPE_HEADER");
        View view = header.getView();
        addFreshViewLayoutParams(view);
        mViewsZAxisNeedReset = true;
        addView(view);
    }

    /**
     * Set the content view<br/>
     * <p>
     * 设置内容视图，`state`内容视图状态，`content`状态对应的视图
     *
     * @param state   The state of content view
     * @param content Content view
     */
    public void setContentView(@State int state, @NonNull View content) {
        switch (state) {
            case STATE_CONTENT:
                if (mContentView != null) {
                    removeView(mContentView);
                }
                mContentResId = View.NO_ID;
                mContentView = content;
                break;
            case STATE_EMPTY:
                if (mEmptyView != null) {
                    removeView(mEmptyView);
                }
                mEmptyLayoutResId = View.NO_ID;
                mEmptyView = content;
                break;
            case STATE_ERROR:
                if (mErrorView != null) {
                    removeView(mErrorView);
                }
                mErrorLayoutResId = View.NO_ID;
                mErrorView = content;
                break;
            case STATE_CUSTOM:
            default:
                if (mCustomView != null) {
                    removeView(mCustomView);
                }
                mCustomLayoutResId = View.NO_ID;
                mCustomView = content;
                break;
        }
        addStateViewLayoutParams(content);
        if (mState != state) {
            content.setVisibility(GONE);
        }
        mViewsZAxisNeedReset = true;
        addView(content);
    }

    /**
     * Update scroller interpolator<br/>
     * <p>
     * 设置Scroller的插值器
     *
     * @param interpolator Scroller interpolator
     */
    public void updateScrollerInterpolator(Interpolator interpolator) {
        mScrollChecker.updateInterpolator(interpolator);
    }

    /**
     * Reset scroller interpolator<br/>
     * <p>
     * 重置Scroller的插值器
     */
    public void resetScrollerInterpolator() {
        mScrollChecker.updateInterpolator(mSpringInterpolator);
    }

    /**
     * Set the scroller default interpolator<br/>
     * <p>
     * 设置Scroller的默认插值器
     *
     * @param interpolator Scroller interpolator
     */
    public void setSpringInterpolator(Interpolator interpolator) {
        mSpringInterpolator = interpolator;
    }

    /**
     * Set the scroller interpolator when in cross boundary rebound<br/>
     * <p>
     * 设置触发越界回弹时候Scroller的插值器
     *
     * @param interpolator Scroller interpolator
     */
    public void setOverScrollInterpolator(Interpolator interpolator) {
        mOverScrollInterpolator = interpolator;
    }

    /**
     * Is in over scrolling
     *
     * @return Is
     */
    public boolean isOverScrolling() {
        return mOverScrollChecker.mScrolling;
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p != null && p instanceof LayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    /**
     * Returns the {@link View} associated with the {@link SmoothRefreshLayout.State}<br/>
     * <p>
     * 得到状态对应的内容视图
     *
     * @param state The view
     */
    public View getView(@State int state) {
        switch (state) {
            case STATE_CONTENT:
                ensureContentView();
                return mContentView;
            case STATE_ERROR:
                ensureErrorView();
                return mErrorView;
            case STATE_EMPTY:
                ensureEmptyView();
                return mEmptyView;
            case STATE_CUSTOM:
            default:
                ensureCustomView();
                return mCustomView;
        }
    }

    /**
     * Returns the current state<br/>
     * <p>
     * 获取当前的状态
     *
     * @return Current state
     */
    @State
    public int getState() {
        return mState;
    }

    /**
     * Set the current state<br/>
     * <p>
     * 设置当前的状态
     *
     * @param state Current state
     */
    @SuppressWarnings({"unused"})
    public void setState(@State int state) {
        setState(state, false);
    }

    /**
     * Set the current state<br/>
     * <p>
     * 设置当前的状态，`state`状态，`animate`动画过渡
     *
     * @param state   Current state
     * @param animate Use animation
     */
    public void setState(@State final int state, final boolean animate) {
        if (state != mState) {
            if (mChangeStateAnimator != null && mChangeStateAnimator.isRunning()) {
                mChangeStateAnimator.cancel();
                mChangeStateAnimator = null;
            }
            final View previousView = getView(mState);
            final View currentView = getView(state);
            if (animate) {
                mChangeStateAnimator = ObjectAnimator.ofFloat(1.0f, 0.0f).setDuration(250L);
                mChangeStateAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        currentView.setVisibility(View.VISIBLE);
                        currentView.setAlpha(0);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        previousView.setVisibility(View.GONE);
                        previousView.setAlpha(1);
                        currentView.setAlpha(1);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        previousView.setVisibility(View.GONE);
                        previousView.setAlpha(1);
                    }
                });
                mChangeStateAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        float value = (float) animation.getAnimatedValue();
                        previousView.setAlpha(value);
                        currentView.setAlpha(1f - value);
                    }
                });
                mChangeStateAnimator.start();
            } else {
                previousView.setVisibility(GONE);
                currentView.setVisibility(VISIBLE);
            }
            mPreviousState = mState;
            mState = state;
            mTargetView = currentView;
            if (mStateChangedListener != null)
                mStateChangedListener.onStateChanged(state);
        }
    }

    @Override
    public boolean onFling(float vx, float vy) {
        if ((isDisabledLoadMore() && isDisabledRefresh())
                || (!isAutoRefresh() && (isNeedInterceptTouchEvent() || isCanNotAbortOverScrolling())))
            return false;
        if ((!canChildScrollUp() && vy > 0) || (!canChildScrollDown() && vy < 0))
            return false;
        if (!isEnabledPinRefreshViewWhileLoading() && !mIndicator.isInStartPosition()) {
            if (Math.abs(vx) <= Math.abs(vy) || Math.abs(vy) >= 1000 || !mIsFingerInsideHorizontalView
                    || !isEnabledKeepRefreshView() || (!isRefreshing() && !isLoadingMore())) {
                final int maxVelocity = ViewConfiguration.get(getContext()).getScaledMaximumFlingVelocity();
                if (Math.abs(vy) > 1000)
                    mScrollChecker.tryToScrollTo(IIndicator.DEFAULT_START_POS,
                            (int) Math.pow(Math.abs(vy), 1 - (Math.abs(vy) / maxVelocity)));
                else
                    mScrollChecker.tryToScrollTo(IIndicator.DEFAULT_START_POS,
                            (int) Math.pow(Math.abs(vy), 1 - (Math.abs(vy * .92f) / maxVelocity)));
            }
            return true;
        }
        //开启到底部自动加载更多和到顶自动刷新
        if ((isEnabledScrollToBottomAutoLoadMore() && !isDisabledPerformLoadMore() && vy < 0)
                || (isEnabledScrollToTopAutoRefresh() && !isDisabledPerformRefresh() && vy > 0))
            vy = vy * 2;
        mOverScrollChecker.fling(vy);
        return true;
    }

    // NestedScrollingChild
    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        if (sDebug) {
            SRLog.d(TAG, "onStartNestedScroll(): nestedScrollAxes: %s", nestedScrollAxes);
        }
        return isEnabled() && isNestedScrollingEnabled()
                && (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public void onNestedScrollAccepted(View child, View target, int axes) {
        if (sDebug) {
            SRLog.d(TAG, "onNestedScrollAccepted(): axes: %s", axes);
        }
        // Reset the counter of how much leftover scroll needs to be consumed.
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);
        mIndicator.onFingerDown();
        // Dispatch up to the nested parent
        startNestedScroll(axes & ViewCompat.SCROLL_AXIS_VERTICAL);
        mNestedScrollInProgress = true;
        if (!mNeedInterceptTouchEventInOnceTouch && !mIsLastOverScrollCanNotAbort) {
            mScrollChecker.abortIfWorking();
            mOverScrollChecker.abortIfWorking();
        }
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        if (sDebug) {
            SRLog.d(TAG, "onNestedPreScroll(): dx: %s, dy: %s, consumed: %s",
                    dx, dy, Arrays.toString(consumed));
        }
        if (mNeedInterceptTouchEventInOnceTouch || mIsLastOverScrollCanNotAbort) {
            consumed[1] = dy;
            onNestedPreScroll(dx, dy, consumed);
            return;
        }
        if (!mIndicator.hasTouched()) {
            if (sDebug) {
                SRLog.w(TAG, "onNestedPreScroll(): There was an exception in touch event handling，" +
                        "This method should be performed after the onNestedScrollAccepted() " +
                        "method is called");
            }
            onNestedPreScroll(dx, dy, consumed);
            return;
        }
        if (dy > 0 && !isDisabledRefresh() && !canChildScrollUp()
                && !(isEnabledPinRefreshViewWhileLoading() && isRefreshing()
                && mIndicator.isOverOffsetToKeepHeaderWhileLoading())) {
            if (!mIndicator.isInStartPosition()) {
                mIndicator.onFingerMove(mIndicator.getLastMovePoint()[0] - dx,
                        mIndicator.getLastMovePoint()[1] - dy);
                moveHeaderPos(mIndicator.getOffsetY());
                consumed[1] = dy;
            } else {
                mIndicator.onFingerMove(mIndicator.getLastMovePoint()[0] - dx,
                        mIndicator.getLastMovePoint()[1]);
            }
        }
        if (dy < 0 && !isDisabledLoadMore() && !canChildScrollDown()
                && !(isEnabledPinRefreshViewWhileLoading() && isLoadingMore()
                && mIndicator.isOverOffsetToKeepFooterWhileLoading())) {
            if (!mIndicator.isInStartPosition() && isMovingFooter()) {
                mIndicator.onFingerMove(mIndicator.getLastMovePoint()[0] - dx,
                        mIndicator.getLastMovePoint()[1] - dy);
                moveFooterPos(mIndicator.getOffsetY());
                consumed[1] = dy;
            } else {
                mIndicator.onFingerMove(mIndicator.getLastMovePoint()[0] - dx,
                        mIndicator.getLastMovePoint()[1]);
            }
        }
        if (dy == 0) {
            mIndicator.onFingerMove(mIndicator.getLastMovePoint()[0] - dx,
                    mIndicator.getLastMovePoint()[1]);
            updateXPos();
        } else if (isMovingFooter() && mViewStatus == SR_VIEW_STATUS_FOOTER_IN_PROCESSING
                && mIndicator.hasLeftStartPosition() && canChildScrollDown()) {
            mScrollChecker.tryToScrollTo(IIndicator.DEFAULT_START_POS, 0);
            consumed[1] = dy;
        }
        tryToResetMovingStatus();
        onNestedPreScroll(dx, dy, consumed);
    }

    private void onNestedPreScroll(int dx, int dy, int[] consumed) {
        // Now let our nested parent consume the leftovers
        final int[] parentConsumed = mParentScrollConsumed;
        if (dispatchNestedPreScroll(dx - consumed[0], dy - consumed[1], parentConsumed, null)) {
            consumed[0] += parentConsumed[0];
            consumed[1] += parentConsumed[1];
        }
    }

    @Override
    public int getNestedScrollAxes() {
        return mNestedScrollingParentHelper.getNestedScrollAxes();
    }

    @Override
    public void onStopNestedScroll(View target) {
        if (sDebug) {
            SRLog.d(TAG, "onStopNestedScroll()");
        }
        mNestedScrollingParentHelper.onStopNestedScroll(target);
        if (mNestedScrollInProgress) {
            mIndicator.onFingerUp();
        }
        mGestureDetector.onDetached();
        mNestedScrollInProgress = false;
        mNeedInterceptTouchEventInOnceTouch = isNeedInterceptTouchEvent();
        mIsLastOverScrollCanNotAbort = isCanNotAbortOverScrolling();
        // Dispatch up our nested parent
        stopNestedScroll();
        if (isAutoRefresh() && mScrollChecker.mIsRunning)
            return;
        if (mIndicator.hasLeftStartPosition()) {
            onFingerUp(false);
        } else {
            notifyFingerUp();
        }
    }

    @Override
    public void onNestedScroll(final View target, final int dxConsumed, final int dyConsumed,
                               final int dxUnconsumed, final int dyUnconsumed) {
        if (sDebug) {
            SRLog.d(TAG, "onNestedScroll(): dxConsumed: %s, dyConsumed: %s, dxUnconsumed: %s" +
                    " dyUnconsumed: %s", dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed);
        }
        if (mNeedInterceptTouchEventInOnceTouch || mIsLastOverScrollCanNotAbort)
            return;
        // Dispatch up to the nested parent first
        dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, mParentOffsetInWindow);
        if (!mIndicator.hasTouched()) {
            if (sDebug) {
                SRLog.w(TAG, "onNestedScroll(): There was an exception in touch event handling，" +
                        "This method should be performed after the onNestedScrollAccepted() " +
                        "method is called");
            }
            return;
        }
        final int dy = dyUnconsumed + mParentOffsetInWindow[1];
        if (dy < 0 && !isDisabledRefresh() && !canChildScrollUp()
                && !(isEnabledPinRefreshViewWhileLoading() && isRefreshing()
                && mIndicator.isOverOffsetToKeepHeaderWhileLoading())) {
            float distance = mIndicator.getCanMoveTheMaxDistanceOfHeader();
            if (distance > 0 && mIndicator.getCurrentPosY() >= distance)
                return;
            mIndicator.onFingerMove(mIndicator.getLastMovePoint()[0],
                    mIndicator.getLastMovePoint()[1] - dy);
            if (distance > 0 && (mIndicator.getCurrentPosY() + mIndicator.getOffsetY() > distance))
                moveHeaderPos(distance - mIndicator.getCurrentPosY());
            else
                moveHeaderPos(mIndicator.getOffsetY());
        } else if (dy > 0 && !isDisabledLoadMore() && !canChildScrollDown()
                && !(isEnabledPinRefreshViewWhileLoading() && isLoadingMore()
                && mIndicator.isOverOffsetToKeepFooterWhileLoading())) {
            float distance = mIndicator.getCanMoveTheMaxDistanceOfFooter();
            if (distance > 0 && mIndicator.getCurrentPosY() > distance)
                return;
            mIndicator.onFingerMove(mIndicator.getLastMovePoint()[0],
                    mIndicator.getLastMovePoint()[1] - dy);
            if (distance > 0 && (mIndicator.getCurrentPosY() - mIndicator.getOffsetY() > distance))
                moveFooterPos(mIndicator.getCurrentPosY() - distance);
            else
                moveFooterPos(mIndicator.getOffsetY());
        }
        tryToResetMovingStatus();
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mNestedScrollingChildHelper.isNestedScrollingEnabled();
    }

    // NestedScrollingChild
    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        mNestedScrollingChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return mNestedScrollingChildHelper.startNestedScroll(axes);
    }

    @Override
    public void stopNestedScroll() {
        mNestedScrollingChildHelper.stopNestedScroll();
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return mNestedScrollingChildHelper.hasNestedScrollingParent();
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
                                        int dyUnconsumed, int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed,
                dxUnconsumed, dyUnconsumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed,
                                           int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedPreScroll(
                dx, dy, consumed, offsetInWindow);
    }

    @Override
    public boolean onNestedPreFling(View target, float velocityX,
                                    float velocityY) {
        if (isEnabledOverScroll())
            return !onFling(velocityX, -velocityY) || dispatchNestedPreFling(velocityX, velocityY);
        else return dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY,
                                 boolean consumed) {
        return dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override
    public void onScrollChanged() {
        if (mNeedFilterScrollEvent) {
            mNeedFilterScrollEvent = false;
            return;
        }
        tryToPerformScrollToBottomToLoadMore();
        mOverScrollChecker.computeScrollOffset();
    }

    /**
     * Check the Z-Axis relationships of the views need to be rearranged
     */
    protected void checkViewsZAxisNeedReset() {
        final int count = getChildCount();
        if (mViewsZAxisNeedReset && count > 0) {
            mCachedViews.clear();
            final boolean isEnabledHeaderDrawer = isEnabledHeaderDrawerStyle();
            final boolean isEnabledFooterDrawer = isEnabledFooterDrawerStyle();
            if (isEnabledHeaderDrawer && isEnabledFooterDrawer) {
                for (int i = count - 1; i >= 0; i--) {
                    View view = getChildAt(i);
                    if (view != mHeaderView.getView() && view != mFooterView.getView())
                        mCachedViews.add(view);
                }
            } else if (isEnabledHeaderDrawer) {
                for (int i = count - 1; i >= 0; i--) {
                    View view = getChildAt(i);
                    if (view != mHeaderView.getView())
                        mCachedViews.add(view);
                }
            } else if (isEnabledFooterDrawer) {
                for (int i = count - 1; i >= 0; i--) {
                    View view = getChildAt(i);
                    if (view != mFooterView.getView())
                        mCachedViews.add(view);
                }
            } else {
                for (int i = count - 1; i >= 0; i--) {
                    View view = getChildAt(i);
                    if (view != mTargetView)
                        mCachedViews.add(view);
                }
            }
            final int viewCount = mCachedViews.size();
            if (viewCount > 0) {
                for (int i = viewCount - 1; i >= 0; i--) {
                    mCachedViews.get(i).bringToFront();
                }
            }
            mCachedViews.clear();
            if (sDebug) {
                SRLog.d(TAG, "checkViewsZAxisNeedReset()");
            }
        }
        mViewsZAxisNeedReset = false;
    }

    protected void destroy() {
        reset();
        if (mUIPositionChangedListeners != null)
            mUIPositionChangedListeners.clear();
        if (sDebug) {
            SRLog.i(TAG, "destroy()");
        }
    }

    protected void reset() {
        if (!mIndicator.isInStartPosition()) {
            mScrollChecker.tryToScrollTo(IIndicator.DEFAULT_START_POS, 0);
        }
        if (!tryToNotifyReset()) {
            mScrollChecker.destroy();
            mOverScrollChecker.destroy();
        }
        mPreviousState = -1;
        mGestureDetector.onDetached();
        removeCallbacks(mScrollChecker);
        removeCallbacks(mOverScrollChecker);
        if (mDelayToRefreshComplete != null)
            removeCallbacks(mDelayToRefreshComplete);
        if (mHeaderRefreshCompleteHook != null)
            mHeaderRefreshCompleteHook.mLayout = null;
        mHeaderRefreshCompleteHook = null;
        if (mFooterRefreshCompleteHook != null)
            mFooterRefreshCompleteHook.mLayout = null;
        mFooterRefreshCompleteHook = null;
        if (mChangeStateAnimator != null && mChangeStateAnimator.isRunning())
            mChangeStateAnimator.cancel();
        if (getHandler() != null)
            getHandler().removeCallbacksAndMessages(null);
        if (sDebug) {
            SRLog.i(TAG, "reset()");
        }
    }

    protected void tryToPerformAutoRefresh() {
        if (isAutoRefresh() && !mAutomaticActionTriggered) {
            if (sDebug) {
                SRLog.d(TAG, "tryToPerformAutoRefresh()");
            }
            if (isHeaderInProcessing()) {
                if (mHeaderView == null || mIndicator.getHeaderHeight() <= 0)
                    return;
                mAutomaticActionTriggered = true;
                mScrollChecker.tryToScrollTo(mIndicator.getOffsetToRefresh(),
                        mAutomaticActionUseSmoothScroll ? mDurationToCloseHeader : 0);
                mAutomaticActionInScrolling = mAutomaticActionUseSmoothScroll;
            } else if (isFooterInProcessing()) {
                if (mFooterView == null || mIndicator.getFooterHeight() <= 0)
                    return;
                mAutomaticActionTriggered = true;
                mScrollChecker.tryToScrollTo(mIndicator.getOffsetToLoadMore(),
                        mAutomaticActionUseSmoothScroll ? mDurationToCloseFooter : 0);
                mAutomaticActionInScrolling = mAutomaticActionUseSmoothScroll;
            }
        }
    }

    protected void ensureFreshView(View child) {
        if (child instanceof IRefreshView) {
            IRefreshView view = (IRefreshView) child;
            switch (view.getType()) {
                case IRefreshView.TYPE_HEADER:
                    if (mHeaderView != null)
                        throw new IllegalArgumentException("Unsupported operation , " +
                                "HeaderView only can be add once !!");
                    mHeaderView = view;
                    break;
                case IRefreshView.TYPE_FOOTER:
                    if (mFooterView != null)
                        throw new IllegalArgumentException("Unsupported operation , " +
                                "FooterView only can be add once !!");
                    mFooterView = view;
                    break;
            }
        }
    }

    protected void ensureContentView() {
        if (mContentView == null) {
            if (mContentResId != View.NO_ID) {
                for (int i = getChildCount() - 1; i >= 0; i--) {
                    View child = getChildAt(i);
                    if (!(child instanceof IRefreshView) && mContentResId == child.getId()) {
                        mContentView = child;
                        break;
                    }
                }
            } else {
                for (int i = getChildCount() - 1; i >= 0; i--) {
                    View child = getChildAt(i);
                    if ((mEmptyView != null && child == mEmptyView)
                            || (mErrorView != null && child == mErrorView)
                            || (mCustomView != null && child == mCustomView))
                        continue;
                    if (child.getVisibility() != GONE && !(child instanceof IRefreshView)) {
                        mContentView = child;
                        break;
                    }
                }
            }
        }
    }

    private void ensureErrorView() {
        if (mErrorView == null && mErrorLayoutResId != NO_ID) {
            mErrorView = mInflater.inflate(mErrorLayoutResId, null, false);
            addStateViewLayoutParams(mErrorView);
            addView(mErrorView);
        } else if (mErrorView == null)
            throw new IllegalArgumentException("Error view must be not null");
    }

    private void ensureEmptyView() {
        if (mEmptyView == null && mEmptyLayoutResId != NO_ID) {
            mEmptyView = mInflater.inflate(mEmptyLayoutResId, null, false);
            addStateViewLayoutParams(mEmptyView);
            addView(mEmptyView);
        } else if (mEmptyView == null)
            throw new IllegalArgumentException("Empty view must be not null");
    }

    private void ensureCustomView() {
        if (mCustomView == null && mCustomLayoutResId != NO_ID) {
            mCustomView = mInflater.inflate(mCustomLayoutResId, null, false);
            addStateViewLayoutParams(mCustomView);
            addView(mCustomView);
        } else if (mCustomView == null)
            throw new IllegalArgumentException("Custom view must be not null");
    }

    private void addFreshViewLayoutParams(View view) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) {
            lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            view.setLayoutParams(lp);
        }
    }

    private void addStateViewLayoutParams(View view) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) {
            lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            view.setLayoutParams(lp);
        }
    }

    private void ensureTargetView() {
        if (mTargetView == null) {
            switch (mState) {
                case STATE_CONTENT:
                    ensureContentView();
                    mTargetView = mContentView;
                    break;
                case STATE_EMPTY:
                    ensureEmptyView();
                    mTargetView = mEmptyView;
                    break;
                case STATE_ERROR:
                    ensureErrorView();
                    mTargetView = mErrorView;
                    break;
                case STATE_CUSTOM:
                default:
                    ensureCustomView();
                    mTargetView = mCustomView;
                    break;
            }
            if (mTargetView == null) {
                throw new RuntimeException("The content view is empty." +
                        " Do you forget to added it in the XML layout file or add it in code ?");
            } else {
                if (isEnabledOverScroll())
                    mTargetView.setOverScrollMode(OVER_SCROLL_NEVER);
            }
        }
        ViewTreeObserver observer;
        if (mLoadMoreScrollTargetView == null) {
            observer = mTargetView.getViewTreeObserver();
        } else {
            observer = mLoadMoreScrollTargetView.getViewTreeObserver();
            if (isEnabledOverScroll())
                mLoadMoreScrollTargetView.setOverScrollMode(OVER_SCROLL_NEVER);
        }
        if (observer != mTargetViewTreeObserver && observer.isAlive()) {
            safelyRemoveListeners();
            mTargetViewTreeObserver = observer;
            mTargetViewTreeObserver.addOnScrollChangedListener(this);
        }
        //Use the static default creator to create the header view
        if (!isDisabledRefresh() && !isEnabledHideHeaderView() && mHeaderView == null && sCreator != null) {
            sCreator.createHeader(this);
        }
        //Use the static default creator to create the footer view
        if (!isDisabledLoadMore() && !isEnabledHideFooterView() && mFooterView == null && sCreator != null) {
            sCreator.createFooter(this);
        }
    }

    protected boolean processDispatchTouchEvent(MotionEvent ev) {
        final int action = MotionEventCompat.getActionMasked(ev);
        if (sDebug) {
            SRLog.d(TAG, "processDispatchTouchEvent(): action: %s", action);
        }
        switch (action) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mPreventForHorizontal = false;
                mDealHorizontalMove = false;
                mIsFingerInsideHorizontalView = false;
                mAutoRefreshBeenSendTouchEvent = false;
                mIndicator.onFingerUp();
                if (mNeedInterceptTouchEventInOnceTouch || mIsLastOverScrollCanNotAbort) {
                    mNeedInterceptTouchEventInOnceTouch = false;
                    if (mIsLastOverScrollCanNotAbort && mIndicator.isInStartPosition()) {
                        mOverScrollChecker.abortIfWorking();
                    }
                    mIsLastOverScrollCanNotAbort = false;
                    float offsetX, offsetY;
                    float[] pressDownPoint = mIndicator.getFingerDownPoint();
                    offsetX = ev.getX() - pressDownPoint[0];
                    offsetY = ev.getY() - pressDownPoint[1];
                    if (Math.abs(offsetX) > mTouchSlop || Math.abs(offsetY) > mTouchSlop) {
                        sendCancelEvent(false);
                        return true;
                    } else {
                        return super.dispatchTouchEvent(ev);
                    }
                } else {
                    mNeedInterceptTouchEventInOnceTouch = false;
                    mIsLastOverScrollCanNotAbort = false;
                    if (mIndicator.hasLeftStartPosition()) {
                        onFingerUp(false);
                        if (mIndicator.hasMovedAfterPressedDown()) {
                            sendCancelEvent(false);
                            return true;
                        }
                        return super.dispatchTouchEvent(ev);
                    } else {
                        notifyFingerUp();
                        return super.dispatchTouchEvent(ev);
                    }
                }
            case MotionEvent.ACTION_POINTER_UP:
                final int actionIndex = MotionEventCompat.getActionIndex(ev);
                if (ev.getPointerId(actionIndex) == mTouchPointerId) {
                    // Pick a new pointer to pick up the slack.
                    final int newIndex = actionIndex == 0 ? 1 : 0;
                    mTouchPointerId = ev.getPointerId(newIndex);
                    mIndicator.onFingerMove(ev.getX(newIndex), ev.getY(newIndex));
                }
                break;
            case MotionEvent.ACTION_DOWN:
                mIndicator.onFingerUp();
                mHasSendDownEvent = false;
                mTouchPointerId = ev.getPointerId(0);
                mIndicator.onFingerDown(ev.getX(), ev.getY());
                mIsFingerInsideHorizontalView = isDisabledWhenHorizontalMove()
                        && (!isEnableCheckFingerInsideHorView()
                        || isFingerInsideHorizontalView(ev.getRawX(), ev.getRawY()));
                mNeedInterceptTouchEventInOnceTouch = isNeedInterceptTouchEvent();
                mIsLastOverScrollCanNotAbort = isCanNotAbortOverScrolling();
                if (!mNeedInterceptTouchEventInOnceTouch && !mIsLastOverScrollCanNotAbort) {
                    mScrollChecker.abortIfWorking();
                    mOverScrollChecker.abortIfWorking();
                }
                mHasSendCancelEvent = false;
                mPreventForHorizontal = false;
                super.dispatchTouchEvent(ev);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!mIndicator.hasTouched()) {
                    return super.dispatchTouchEvent(ev);
                }
                final int index = ev.findPointerIndex(mTouchPointerId);
                if (index < 0) {
                    Log.e(TAG, "Error processing scroll; pointer index for id " +
                            mTouchPointerId + " not found. Did any MotionEvents get skipped?");
                    return super.dispatchTouchEvent(ev);
                }
                mLastMoveEvent = ev;
                if (mNeedInterceptTouchEventInOnceTouch) {
                    mOverScrollChecker.abortIfWorking();
                    if (mIndicator.isInStartPosition() && !mScrollChecker.mIsRunning) {
                        makeNewTouchDownEvent(ev);
                        mNeedInterceptTouchEventInOnceTouch = false;
                    }
                    return true;
                }
                if (mIsLastOverScrollCanNotAbort) {
                    if (mIndicator.isInStartPosition() && !mOverScrollChecker.mScrolling) {
                        makeNewTouchDownEvent(ev);
                        mIsLastOverScrollCanNotAbort = false;
                    }
                    return true;
                }
                tryToResetMovingStatus();
                final float[] lastMovePoint = mIndicator.getLastMovePoint().clone();
                mIndicator.onFingerMove(ev.getX(index), ev.getY(index));
                float offsetX, offsetY;
                final float[] pressDownPoint = mIndicator.getFingerDownPoint();
                offsetX = ev.getX(index) - pressDownPoint[0];
                offsetY = ev.getY(index) - pressDownPoint[1];
                final boolean canNotChildScrollDown = !canChildScrollDown();
                final boolean canNotChildScrollUp = !canChildScrollUp();
                if (isDisabledWhenHorizontalMove() && mIsFingerInsideHorizontalView) {
                    if (!mDealHorizontalMove) {
                        if ((Math.abs(offsetX) >= mTouchSlop
                                && Math.abs(offsetX) > Math.abs(offsetY))) {
                            mPreventForHorizontal = true;
                            mDealHorizontalMove = true;
                        } else if (Math.abs(offsetX) < mTouchSlop
                                && Math.abs(offsetY) < mTouchSlop) {
                            mDealHorizontalMove = false;
                            mPreventForHorizontal = true;
                        } else {
                            mDealHorizontalMove = true;
                            mPreventForHorizontal = false;
                        }
                    } else if (mPreventForHorizontal) {
                        if ((isMovingHeader() && !canNotChildScrollUp)
                                || (isMovingFooter() && !canNotChildScrollDown)) {
                            mHasSendDownEvent = false;
                            offsetX = ev.getX(index) - lastMovePoint[0];
                            offsetY = ev.getY(index) - lastMovePoint[1];
                            mPreventForHorizontal = Math.abs(offsetX) >= Math.abs(offsetY);
                        }
                    }
                } else {
                    if (Math.abs(offsetX) < mTouchSlop
                            && Math.abs(offsetY) < mTouchSlop) {
                        return super.dispatchTouchEvent(ev);
                    }
                }
                if (mPreventForHorizontal) {
                    return super.dispatchTouchEvent(ev);
                }
                offsetY = mIndicator.getOffsetY();
                int currentY = mIndicator.getCurrentPosY();
                boolean movingDown = offsetY > 0;
                if (isMovingFooter() && isFooterInProcessing()
                        && mIndicator.hasLeftStartPosition() && !canNotChildScrollDown) {
                    mScrollChecker.tryToScrollTo(IIndicator.DEFAULT_START_POS, 0);
                    return super.dispatchTouchEvent(ev);
                }
                float maxHeaderDistance = mIndicator.getCanMoveTheMaxDistanceOfHeader();
                if (movingDown && isMovingHeader() && !mIndicator.isInStartPosition()
                        && maxHeaderDistance > 0) {
                    if (currentY >= maxHeaderDistance) {
                        updateXPos();
                        return super.dispatchTouchEvent(ev);
                    } else if (currentY + offsetY > maxHeaderDistance) {
                        moveHeaderPos(maxHeaderDistance - currentY);
                        return true;
                    }
                }
                float maxFooterDistance = mIndicator.getCanMoveTheMaxDistanceOfFooter();
                if (!movingDown && isMovingFooter() && !mIndicator.isInStartPosition()
                        && maxFooterDistance > 0) {
                    if (currentY >= maxFooterDistance) {
                        updateXPos();
                        return super.dispatchTouchEvent(ev);
                    } else if (currentY - offsetY > maxFooterDistance) {
                        moveFooterPos(currentY - maxFooterDistance);
                        return true;
                    }
                }
                boolean canMoveUp = isMovingHeader() && mIndicator.hasLeftStartPosition();
                boolean canMoveDown = isMovingFooter() && mIndicator.hasLeftStartPosition();
                boolean canHeaderMoveDown = canNotChildScrollUp && !isDisabledRefresh();
                boolean canFooterMoveUp = canNotChildScrollDown && !isDisabledLoadMore();
                if (!canMoveUp && !canMoveDown) {
                    if ((movingDown && !canHeaderMoveDown) || (!movingDown && !canFooterMoveUp)) {
                        if (isLoadingMore() && mIndicator.hasLeftStartPosition()) {
                            moveFooterPos(offsetY);
                            return true;
                        } else if (isRefreshing() && mIndicator.hasLeftStartPosition()) {
                            moveHeaderPos(offsetY);
                            return true;
                        } else if (isAutoRefresh() && !mAutoRefreshBeenSendTouchEvent) {
                            // When the Auto-Refresh is in progress, the content view can not
                            // continue to move up when the content view returns to the top
                            // 当自动刷新正在进行时，移动内容视图返回到顶部后无法继续向上移动
                            makeNewTouchDownEvent(ev);
                            mAutoRefreshBeenSendTouchEvent = true;
                        }
                        return super.dispatchTouchEvent(ev);
                    }
                    // should show up header
                    if (movingDown) {
                        if (isDisabledRefresh())
                            return super.dispatchTouchEvent(ev);
                        moveHeaderPos(offsetY);
                        return true;
                    }
                    if (isDisabledLoadMore())
                        return super.dispatchTouchEvent(ev);
                    moveFooterPos(offsetY);
                    return true;
                }
                if (canMoveUp) {
                    if (isDisabledRefresh())
                        return super.dispatchTouchEvent(ev);
                    if ((!canHeaderMoveDown && movingDown)) {
                        sendDownEvent(false);
                        return super.dispatchTouchEvent(ev);
                    }
                    moveHeaderPos(offsetY);
                    return true;
                }
                if (isDisabledLoadMore())
                    return super.dispatchTouchEvent(ev);
                if ((!canFooterMoveUp && !movingDown)) {
                    sendDownEvent(false);
                    return super.dispatchTouchEvent(ev);
                }
                moveFooterPos(offsetY);
                return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    protected boolean isNeedInterceptTouchEvent() {
        return (isEnabledInterceptEventWhileLoading() && (isRefreshing() || isLoadingMore()))
                || mChangeStateAnimator != null && mChangeStateAnimator.isRunning()
                || (isAutoRefresh() && mAutomaticActionInScrolling);
    }

    protected boolean isCanNotAbortOverScrolling() {
        return (mOverScrollChecker.mScrolling
                && (((isMovingHeader() && isDisabledRefresh()))
                || (isMovingFooter() && isDisabledLoadMore())));
    }

    protected boolean canChildScrollUp() {
        if (mScrollUpCallback != null)
            return mScrollUpCallback.canChildScrollUp(this, mTargetView, mHeaderView);
        return ScrollCompat.canChildScrollUp(mTargetView);
    }

    protected boolean canChildScrollDown() {
        if (mScrollDownCallback != null)
            return mScrollDownCallback.canChildScrollDown(this, mTargetView, mFooterView);
        return ScrollCompat.canChildScrollDown(mTargetView);
    }

    protected boolean isFingerInsideHorizontalView(final float x, final float y) {
        if (mFingerInsideHorizontalViewCallback != null)
            return mFingerInsideHorizontalViewCallback.isFingerInside(x, y, mTargetView);
        return BoundaryUtil.isFingerInsideHorizontalView(x, y, mTargetView);
    }

    private void makeNewTouchDownEvent(MotionEvent ev) {
        final boolean isNeedDetectGesture = isEnabledOverScroll();
        sendCancelEvent(isNeedDetectGesture);
        sendDownEvent(isNeedDetectGesture);
        mIndicator.onFingerUp();
        mIndicator.onFingerDown(ev.getX(), ev.getY());
    }

    private void sendCancelEvent(boolean eventNeedDetectGesture) {
        if (mHasSendCancelEvent || mLastMoveEvent == null) return;
        if (sDebug) {
            SRLog.i(TAG, "sendCancelEvent(): eventNeedDetectGesture: %s", eventNeedDetectGesture);
        }
        final MotionEvent last = mLastMoveEvent;
        MotionEvent ev = MotionEvent.obtain(last.getDownTime(), last.getEventTime() +
                        ViewConfiguration.getLongPressTimeout(),
                MotionEvent.ACTION_CANCEL, last.getX(), last.getY(), last.getMetaState());
        if (eventNeedDetectGesture) {
            mGestureDetector.onTouchEvent(ev);
        }
        mHasSendCancelEvent = true;
        mHasSendDownEvent = false;
        super.dispatchTouchEvent(ev);
    }

    private void sendDownEvent(boolean eventNeedDetectGesture) {
        if (mHasSendDownEvent || mLastMoveEvent == null) return;
        if (sDebug) {
            SRLog.i(TAG, "sendDownEvent(): eventNeedDetectGesture: %s", eventNeedDetectGesture);
        }
        final MotionEvent last = mLastMoveEvent;
        MotionEvent ev = MotionEvent.obtain(last.getDownTime(), last.getEventTime(),
                MotionEvent.ACTION_DOWN, last.getX(), last.getY(), last.getMetaState());
        if (eventNeedDetectGesture) {
            mGestureDetector.onTouchEvent(ev);
        }
        mHasSendCancelEvent = false;
        mHasSendDownEvent = true;
        super.dispatchTouchEvent(ev);
    }

    private void notifyFingerUp() {
        if (sDebug) {
            SRLog.i(TAG, "notifyFingerUp()");
        }
        if (mHeaderView != null && isHeaderInProcessing() && !isDisabledRefresh()) {
            mHeaderView.onFingerUp(this, mIndicator);
        } else if (mFooterView != null && isFooterInProcessing() && !isDisabledLoadMore()) {
            mFooterView.onFingerUp(this, mIndicator);
        }
    }

    protected void onFingerUp(boolean stayForLoading) {
        if (sDebug) {
            SRLog.d(TAG, "onFingerUp(): stayForLoading: %s", stayForLoading);
        }
        notifyFingerUp();
        if (!stayForLoading && isEnabledKeepRefreshView() && mStatus != SR_STATUS_COMPLETE
                && !isRefreshing() && !isLoadingMore()) {
            if (isHeaderInProcessing() && !isDisabledRefresh()
                    && mIndicator.isOverOffsetToKeepHeaderWhileLoading()) {
                if (mIndicator.isAlreadyHere(mIndicator.getOffsetToKeepHeaderWhileLoading())
                        || isDisabledPerformRefresh()) {
                    onRelease(0);
                    return;
                }
                mScrollChecker.tryToScrollTo(mIndicator.getOffsetToKeepHeaderWhileLoading(),
                        mDurationOfBackToHeaderHeight);
            } else if (isFooterInProcessing() && !isDisabledLoadMore()
                    && mIndicator.isOverOffsetToKeepFooterWhileLoading()) {
                if (mIndicator.isAlreadyHere(mIndicator.getOffsetToKeepFooterWhileLoading())
                        || isDisabledPerformLoadMore()) {
                    onRelease(0);
                    return;
                }
                mScrollChecker.tryToScrollTo(mIndicator.getOffsetToKeepFooterWhileLoading(),
                        mDurationOfBackToFooterHeight);
            } else {
                onRelease(0);
            }
        } else {
            onRelease(0);
        }
    }

    protected void onRelease(int duration) {
        if (sDebug) {
            SRLog.d(TAG, "onRelease(): duration: %s", duration);
        }
        mAutomaticActionInScrolling = false;
        tryToPerformRefresh();
        if (mStatus == SR_STATUS_REFRESHING || mStatus == SR_STATUS_LOADING_MORE) {
            if (isEnabledKeepRefreshView()) {
                if (isHeaderInProcessing()) {
                    if (isMovingHeader() && mIndicator.isOverOffsetToKeepHeaderWhileLoading()) {
                        mScrollChecker.tryToScrollTo(mIndicator.getOffsetToKeepHeaderWhileLoading(),
                                mDurationOfBackToHeaderHeight);
                    } else if (isMovingFooter()) {
                        tryScrollBackToTopByPercentDuration(duration);
                    }
                } else if (isFooterInProcessing()) {
                    if (isMovingFooter() && mIndicator.isOverOffsetToKeepFooterWhileLoading()) {
                        mScrollChecker.tryToScrollTo(mIndicator.getOffsetToKeepFooterWhileLoading(),
                                mDurationOfBackToFooterHeight);
                    } else if (isMovingHeader()) {
                        tryScrollBackToTopByPercentDuration(duration);
                    }
                }
            } else {
                tryScrollBackToTopByPercentDuration(duration);
            }
        } else if (mStatus == SR_STATUS_COMPLETE) {
            notifyUIRefreshComplete(true);
        } else {
            tryScrollBackToTopByPercentDuration(duration);
        }
    }

    protected void tryScrollBackToTopByPercentDuration(int duration) {
        //Use the current percentage duration of the current position to scroll back to the top
        float percent;
        if (isMovingHeader()) {
            percent = mIndicator.getCurrentPercentOfRefreshOffset();
            percent = percent > 1 || percent <= 0 ? 1 : percent;
            tryScrollBackToTop(duration > 0 ? duration : Math.round(mDurationToCloseHeader * percent));
        } else if (isMovingFooter()) {
            percent = mIndicator.getCurrentPercentOfLoadMoreOffset();
            percent = percent > 1 || percent <= 0 ? 1 : percent;
            tryScrollBackToTop(duration > 0 ? duration : Math.round(mDurationToCloseFooter * percent));
        } else {
            tryScrollBackToTop(duration);
        }
    }

    protected void tryScrollBackToTop(int duration) {
        if (sDebug) {
            SRLog.d(TAG, "tryScrollBackToTop(): duration: %s", duration);
        }
        if (!mIndicator.hasTouched() || !mIndicator.hasMoved()
                && mIndicator.hasLeftStartPosition()) {
            mScrollChecker.tryToScrollTo(IIndicator.DEFAULT_START_POS, duration);
            return;
        }
        if ((mNeedInterceptTouchEventInOnceTouch || mIsLastOverScrollCanNotAbort)
                && mIndicator.hasLeftStartPosition()) {
            mScrollChecker.tryToScrollTo(IIndicator.DEFAULT_START_POS, duration);
            return;
        }
        if (isMovingFooter() && mStatus == SR_STATUS_COMPLETE
                && mIndicator.hasJustBackToStartPosition()) {
            mScrollChecker.tryToScrollTo(IIndicator.DEFAULT_START_POS, duration);
        }
    }

    protected void notifyUIRefreshComplete(boolean useScroll) {
        if (sDebug) {
            SRLog.i(TAG, "notifyUIRefreshComplete()");
        }
        mIndicator.onRefreshComplete();
        if (mNeedNotifyRefreshComplete) {
            if (isHeaderInProcessing() && mHeaderView != null) {
                mHeaderView.onRefreshComplete(this, mIsLastRefreshSuccessful);
            } else if (isFooterInProcessing() && mFooterView != null) {
                mFooterView.onRefreshComplete(this, mIsLastRefreshSuccessful);
            }
            if (mRefreshListener != null) {
                mRefreshListener.onRefreshComplete(mIsLastRefreshSuccessful);
            }
            mNeedNotifyRefreshComplete = false;
        } else if (mDelayedRefreshComplete) {
            if (mRefreshListener != null) {
                mRefreshListener.onRefreshComplete(mIsLastRefreshSuccessful);
            }
        }
        if (useScroll) tryScrollBackToTopByPercentDuration(0);
        tryToNotifyReset();
    }

    protected void moveHeaderPos(float deltaY) {
        if (sDebug) {
            SRLog.d(TAG, "moveHeaderPos(): deltaY: %s", deltaY);
        }
        mIndicator.setMovingStatus(IIndicator.MOVING_HEADER);
        // to keep the consistence with refresh, need to converse the deltaY
        movePos(deltaY);
    }

    protected void moveFooterPos(float deltaY) {
        if (sDebug) {
            SRLog.d(TAG, "moveFooterPos(): deltaY: %s", deltaY);
        }
        mIndicator.setMovingStatus(IIndicator.MOVING_FOOTER);
        //check if it is needed to compatible scroll
        if (!isEnabledPinContentView() && mIsLastRefreshSuccessful
                && (mStatus == SR_STATUS_COMPLETE
                || (isEnabledNextPtrAtOnce() && mStatus == SR_STATUS_PREPARE
                && !mOverScrollChecker.mScrolling
                && !mIndicator.hasTouched()))) {
            if (sDebug) {
                SRLog.d(TAG, "moveFooterPos(): compatible scroll deltaY: %s", deltaY);
            }
            mNeedFilterScrollEvent = true;
            if (mLoadMoreScrollCallback == null) {
                if (mLoadMoreScrollTargetView != null)
                    ScrollCompat.scrollCompat(mLoadMoreScrollTargetView, deltaY);
                else
                    ScrollCompat.scrollCompat(mTargetView, deltaY);
            } else {
                mLoadMoreScrollCallback.onScroll(mTargetView, deltaY);
            }
        }
        // to keep the consistence with refresh, need to converse the deltaY
        movePos(-deltaY);
    }

    protected void movePos(float deltaY) {
        // has reached the top
        if (deltaY < 0 && mIndicator.isInStartPosition()) {
            if (sDebug) {
                SRLog.d(TAG, "movePos(): has reached the top");
            }
            return;
        }
        int to = mIndicator.getCurrentPosY() + (int) deltaY;
        // over top
        if (mIndicator.willOverTop(to)) {
            to = IIndicator.DEFAULT_START_POS;
            if (sDebug) {
                SRLog.d(TAG, "movePos(): over top");
            }
        }
        mAutoRefreshBeenSendTouchEvent = false;
        mIndicator.setCurrentPos(to);
        int change = to - mIndicator.getLastPosY();
        if (isMovingHeader())
            updateYPos(change);
        else if (isMovingFooter())
            updateYPos(-change);
    }

    /**
     * Update view's Y position
     *
     * @param change The changed value
     */
    @SuppressWarnings({"unchecked"})
    protected void updateYPos(int change) {
        // once moved, cancel event will be sent to child
        if (mIndicator.hasTouched() && !mNestedScrollInProgress
                && mIndicator.hasMovedAfterPressedDown()) {
            sendCancelEvent(false);
        }
        final boolean isMovingHeader = isMovingHeader();
        final boolean isMovingFooter = isMovingFooter();
        // leave initiated position or just refresh complete
        if (((mIndicator.hasJustLeftStartPosition() || mViewStatus == SR_VIEW_STATUS_INIT)
                && mStatus == SR_STATUS_INIT)
                || (mStatus == SR_STATUS_COMPLETE && isEnabledNextPtrAtOnce()
                && ((isHeaderInProcessing() && isMovingHeader && change > 0)
                || (isFooterInProcessing() && isMovingFooter && change < 0)))) {
            mStatus = SR_STATUS_PREPARE;
            if (isMovingHeader()) {
                mViewStatus = SR_VIEW_STATUS_HEADER_IN_PROCESSING;
                if (mHeaderView != null)
                    mHeaderView.onRefreshPrepare(this);
            } else if (isMovingFooter()) {
                mViewStatus = SR_VIEW_STATUS_FOOTER_IN_PROCESSING;
                if (mFooterView != null)
                    mFooterView.onRefreshPrepare(this);
            }
        }
        // back to initiated position
        if (!(isAutoRefresh() && mStatus != SR_STATUS_COMPLETE)
                && mIndicator.hasJustBackToStartPosition()) {
            tryToNotifyReset();
            // recover event to children
            if (mIndicator.hasTouched() && !mNestedScrollInProgress && mHasSendCancelEvent) {
                sendDownEvent(false);
            }
        }
        tryToPerformRefreshWhenMoved();
        if (sDebug) {
            SRLog.d(TAG, "updateYPos(): change: %s, current: %s last: %s",
                    change, mIndicator.getCurrentPosY(), mIndicator.getLastPosY());
        }
        notifyUIPositionChanged();
        if (mHeaderView != null && !isDisabledRefresh() && isMovingHeader
                && !isEnabledHideHeaderView()) {
            if (!isEnabledHeaderDrawerStyle())
                mHeaderView.getView().offsetTopAndBottom(change);
            if (isHeaderInProcessing())
                mHeaderView.onRefreshPositionChanged(this, mStatus, mIndicator);
            else
                mHeaderView.onPureScrollPositionChanged(this, mStatus, mIndicator);
        } else if (mFooterView != null && !isDisabledLoadMore() && isMovingFooter
                && !isEnabledHideFooterView()) {
            if (!isEnabledFooterDrawerStyle())
                mFooterView.getView().offsetTopAndBottom(change);
            if (isFooterInProcessing())
                mFooterView.onRefreshPositionChanged(this, mStatus, mIndicator);
            else
                mFooterView.onPureScrollPositionChanged(this, mStatus, mIndicator);
        }
        if (!isEnabledPinContentView()) {
            if (mLoadMoreScrollTargetView != null && isMovingFooter && !isDisabledLoadMore()) {
                mLoadMoreScrollTargetView.offsetTopAndBottom(change);
            } else {
                mTargetView.offsetTopAndBottom(change);
            }
        }
        boolean needRequestLayout = false;
        if ((isMovingHeader && (mHeaderView != null && mHeaderView.getStyle()
                == IRefreshView.STYLE_SCALE))
                || (isMovingFooter && (mFooterView != null && mFooterView.getStyle()
                == IRefreshView.STYLE_SCALE))) {
            needRequestLayout = true;
        }
        //check need perform refresh
        tryToPerformScrollToTopToRefresh();
        if (needRequestLayout || (!mOverScrollChecker.mScrolling && mIndicator.isInStartPosition())) {
            requestLayout();
        } else {
            invalidate();
        }
    }

    protected void tryToPerformRefreshWhenMoved() {
        // try to perform refresh
        if (!mOverScrollChecker.mScrolling && mStatus == SR_STATUS_PREPARE) {
            // reach fresh height while moving from top to bottom or reach load more height while
            // moving from bottom to top
            if (mIndicator.hasTouched() && !isAutoRefresh() && isEnabledPullToRefresh()
                    && ((isHeaderInProcessing() && isMovingHeader() && mIndicator
                    .crossRefreshLineFromTopToBottom())
                    || (isFooterInProcessing() && isMovingFooter() && mIndicator
                    .crossRefreshLineFromBottomToTop()))) {
                tryToPerformRefresh();
            }
            // reach header height while auto refresh or reach footer height while auto refresh
            if (!isRefreshing() && !isLoadingMore() && isPerformAutoRefreshButLater()
                    && ((isHeaderInProcessing() && isMovingHeader() && mIndicator
                    .hasJustReachedHeaderHeightFromTopToBottom())
                    || (isFooterInProcessing() && isMovingFooter() && mIndicator
                    .hasJustReachedFooterHeightFromBottomToTop()))) {
                tryToPerformRefresh();
            }
        }
    }

    /**
     * We need to notify the X pos changed
     */
    @SuppressWarnings({"unchecked"})
    protected void updateXPos() {
        if (mHeaderView != null && !isDisabledRefresh() && isMovingHeader()
                && !isEnabledHideHeaderView()) {
            if (isHeaderInProcessing())
                mHeaderView.onRefreshPositionChanged(this, mStatus, mIndicator);
            else
                mHeaderView.onPureScrollPositionChanged(this, mStatus, mIndicator);
        } else if (mFooterView != null && !isDisabledLoadMore() && isMovingFooter()
                && !isEnabledHideFooterView()) {
            if (isFooterInProcessing())
                mFooterView.onRefreshPositionChanged(this, mStatus, mIndicator);
            else
                mFooterView.onPureScrollPositionChanged(this, mStatus, mIndicator);
        }
    }

    protected boolean isMovingHeader() {
        return mIndicator.getMovingStatus() == IIndicator.MOVING_HEADER;
    }

    protected boolean isMovingContent() {
        return mIndicator.getMovingStatus() == IIndicator.MOVING_CONTENT;
    }

    protected boolean isMovingFooter() {
        return mIndicator.getMovingStatus() == IIndicator.MOVING_FOOTER;
    }

    protected boolean isHeaderInProcessing() {
        return mViewStatus == SR_VIEW_STATUS_HEADER_IN_PROCESSING;
    }

    protected boolean isFooterInProcessing() {
        return mViewStatus == SR_VIEW_STATUS_FOOTER_IN_PROCESSING;
    }

    protected void tryToScrollTo(int to, int duration) {
        mScrollChecker.tryToScrollTo(to, duration);
    }

    /**
     * Check in over scrolling needs to scroll back to the start position
     *
     * @return Is
     */
    private boolean canSpringBack() {
        if (mOverScrollChecker.mClamped && !mIndicator.isInStartPosition()) {
            if (sDebug) {
                SRLog.i(TAG, "canSpringBack()");
            }
            onRelease(mOverScrollChecker.mDuration);
            mOverScrollChecker.mClamped = false;
            return true;
        } else {
            return false;
        }
    }

    protected boolean tryToNotifyReset() {
        if (sDebug) {
            SRLog.i(TAG, "tryToNotifyReset()");
        }
        if ((mStatus == SR_STATUS_COMPLETE || mStatus == SR_STATUS_PREPARE)
                && (mIndicator.isInStartPosition()
                || (isHeaderInProcessing() != isMovingHeader()
                || (isFooterInProcessing() != isMovingFooter())))) {
            if (mHeaderView != null)
                mHeaderView.onReset(this);
            if (mFooterView != null)
                mFooterView.onReset(this);
            mStatus = SR_STATUS_INIT;
            mViewStatus = SR_VIEW_STATUS_INIT;
            mNeedNotifyRefreshComplete = true;
            mDelayedRefreshComplete = false;
            mOverScrollChecker.destroy();
            mFlag = mFlag & ~MASK_AUTO_REFRESH;
            mAutomaticActionTriggered = false;
            tryToResetMovingStatus();
            return true;
        }
        return false;
    }

    protected void performRefreshComplete(boolean hook) {
        if (sDebug) {
            SRLog.i(TAG, "performRefreshComplete()");
        }
        if (isRefreshing() && hook && mHeaderRefreshCompleteHook != null
                && mHeaderRefreshCompleteHook.mCallBack != null) {
            mHeaderRefreshCompleteHook.mLayout = this;
            mHeaderRefreshCompleteHook.doHook();
            return;
        }
        if (isLoadingMore() && hook && mFooterRefreshCompleteHook != null
                && mFooterRefreshCompleteHook.mCallBack != null) {
            mFooterRefreshCompleteHook.mLayout = this;
            mFooterRefreshCompleteHook.doHook();
            return;
        }
        mStatus = SR_STATUS_COMPLETE;
        notifyUIRefreshComplete(true);
    }

    /**
     * try to perform refresh or loading , if performed return true
     */
    protected void tryToPerformRefresh() {
        // status not be prepare or over scrolling or moving content go to break;
        if (mStatus != SR_STATUS_PREPARE || !canPerformRefresh()) {
            return;
        }
        if (sDebug) {
            SRLog.i(TAG, "tryToPerformRefresh()");
        }
        if (isHeaderInProcessing() && !isDisabledRefresh() && !isDisabledPerformRefresh()
                && ((mIndicator.isOverOffsetToKeepHeaderWhileLoading() && isAutoRefresh())
                || (isEnabledKeepRefreshView() && mIndicator.isOverOffsetToKeepHeaderWhileLoading())
                || mIndicator.isOverOffsetToRefresh())) {
            triggeredRefresh();
            return;
        }
        if (isFooterInProcessing() && !isDisabledLoadMore() && !isDisabledPerformLoadMore()
                && ((mIndicator.isOverOffsetToKeepFooterWhileLoading() && isAutoRefresh())
                || (isEnabledKeepRefreshView() && mIndicator.isOverOffsetToKeepFooterWhileLoading())
                || mIndicator.isOverOffsetToLoadMore())) {
            triggeredLoadMore();
        }
    }

    protected void tryToPerformScrollToBottomToLoadMore() {
        if (isEnabledScrollToBottomAutoLoadMore() && !isDisabledPerformLoadMore()
                && (mStatus == SR_STATUS_INIT || mStatus == SR_STATUS_PREPARE)) {
            if (mAutoLoadMoreCallBack != null && mAutoLoadMoreCallBack.canAutoLoadMore(this,
                    mTargetView)) {
                triggeredLoadMore();
            } else if (mAutoLoadMoreCallBack == null) {
                if (isMovingFooter() && mLoadMoreScrollTargetView != null
                        && ScrollCompat.canAutoLoadMore(mLoadMoreScrollTargetView)) {
                    triggeredLoadMore();
                } else if (ScrollCompat.canAutoLoadMore(mTargetView)) {
                    triggeredLoadMore();
                }
            }
        }
    }

    protected void tryToPerformScrollToTopToRefresh() {
        if (isEnabledScrollToTopAutoRefresh() && !isDisabledPerformRefresh()
                && (mStatus == SR_STATUS_INIT || mStatus == SR_STATUS_PREPARE)
                && mIndicator.hasJustLeftStartPosition()
                && !canChildScrollUp()) {
            triggeredRefresh();
        }
    }

    protected void triggeredRefresh() {
        mStatus = SR_STATUS_REFRESHING;
        mViewStatus = SR_VIEW_STATUS_HEADER_IN_PROCESSING;
        mDelayedRefreshComplete = false;
        performRefresh();
    }

    protected void triggeredLoadMore() {
        mStatus = SR_STATUS_LOADING_MORE;
        mViewStatus = SR_VIEW_STATUS_FOOTER_IN_PROCESSING;
        mDelayedRefreshComplete = false;
        performRefresh();
    }

    protected void tryToResetMovingStatus() {
        if (mIndicator.isInStartPosition() && !isMovingContent()) {
            mIndicator.setMovingStatus(IIndicator.MOVING_CONTENT);
            notifyUIPositionChanged();
        }
    }

    protected boolean canPerformRefresh() {
        return !(mOverScrollChecker.mClamped || mOverScrollChecker.mScrolling
                || isMovingContent());
    }

    /**
     * Try check auto refresh later flag
     *
     * @return Is
     */
    protected boolean isPerformAutoRefreshButLater() {
        return (mFlag & MASK_AUTO_REFRESH) == FLAG_AUTO_REFRESH_BUT_LATER;
    }

    protected void performRefresh() {
        //loading start milliseconds since boot
        mLoadingStartTime = SystemClock.uptimeMillis();
        mNeedNotifyRefreshComplete = true;
        if (sDebug) {
            SRLog.d(TAG, "onRefreshBegin systemTime: %s", mLoadingStartTime);
        }
        if (isRefreshing()) {
            if (mHeaderView != null)
                mHeaderView.onRefreshBegin(this, mIndicator);
        } else if (isLoadingMore()) {
            if (mFooterView != null)
                mFooterView.onRefreshBegin(this, mIndicator);
        }
        if (mRefreshListener != null)
            mRefreshListener.onRefreshBegin(isRefreshing());
    }

    private void notifyUIPositionChanged() {
        if (mUIPositionChangedListeners != null && !mUIPositionChangedListeners.isEmpty()) {
            for (OnUIPositionChangedListener listener : mUIPositionChangedListeners) {
                listener.onChanged(mStatus, mIndicator);
            }
        }
    }

    /**
     * Safely remove the onScrollChangedListener from target ViewTreeObserver
     */
    private void safelyRemoveListeners() {
        if (mTargetViewTreeObserver != null) {
            if (mTargetViewTreeObserver.isAlive())
                mTargetViewTreeObserver.removeOnScrollChangedListener(this);
            else {
                try {
                    Field field = ViewTreeObserver.class.getDeclaredField("mOnScrollChangedListeners");
                    if (field != null) {
                        field.setAccessible(true);
                        Object object = field.get(mTargetViewTreeObserver);
                        if (object != null) {
                            Method method = object.getClass().getDeclaredMethod("remove", Object.class);
                            if (method != null) {
                                method.setAccessible(true);
                                method.invoke(object, this);
                            }
                            method = object.getClass().getDeclaredMethod("size");
                            if (method != null) {
                                method.setAccessible(true);
                                object = method.invoke(object);
                                if (object != null && object instanceof Integer) {
                                    int size = (int) object;
                                    if (size == 0) {
                                        field.set(mTargetViewTreeObserver, null);
                                    }
                                }
                            }
                        }
                    }
                } catch (IllegalAccessException e) {
                    //ignore exception
                } catch (NoSuchFieldException e) {
                    //ignore exception
                } catch (NoSuchMethodException e) {
                    //ignore exception
                } catch (InvocationTargetException e) {
                    //ignore exception
                }
            }
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATE_CONTENT, STATE_ERROR, STATE_EMPTY, STATE_CUSTOM})
    @interface State {
    }

    /**
     * Classes that wish to override {@link SmoothRefreshLayout#canChildScrollUp()} method
     * behavior should implement this interface.
     */
    public interface OnChildScrollUpCallback {
        /**
         * Callback that will be called when {@link SmoothRefreshLayout#canChildScrollUp()} method
         * is called to allow the implementer to override its behavior.
         *
         * @param parent SmoothRefreshLayout that this callback is overriding.
         * @param child  The child view.
         * @param header The header view.
         * @return Whether it is possible for the child view of parent layout to scroll up.
         */
        boolean canChildScrollUp(SmoothRefreshLayout parent, @Nullable View child,
                                 @Nullable IRefreshView header);
    }

    /**
     * Classes that wish to override {@link SmoothRefreshLayout#canChildScrollDown()} method
     * behavior should implement this interface.
     */
    public interface OnChildScrollDownCallback {
        /**
         * Callback that will be called when {@link SmoothRefreshLayout#canChildScrollDown()} method
         * is called to allow the implementer to override its behavior.
         *
         * @param parent SmoothRefreshLayout that this callback is overriding.
         * @param child  The child view.
         * @param footer The footer view.
         * @return Whether it is possible for the child view of parent layout to scroll down.
         */
        boolean canChildScrollDown(SmoothRefreshLayout parent, @Nullable View child,
                                   @Nullable IRefreshView footer);
    }

    /**
     * Classes that wish to override {@link SmoothRefreshLayout#isFingerInsideHorizontalView(float, float)}}
     * method behavior should implement this interface.
     */
    public interface OnFingerInsideHorViewCallback {
        /**
         * Callback that will be called when
         * {@link SmoothRefreshLayout#isFingerInsideHorizontalView(float, float)}} method
         * is called to allow the implementer to override its behavior.
         *
         * @param x    The finger pressed x of the screen.
         * @param y    The finger pressed y of the screen.
         * @param view The target view.
         * @return Whether the finger pressed point is inside horizontal view
         */
        boolean isFingerInside(float x, float y, View view);
    }

    /**
     * Classes that wish to be notified when the swipe gesture correctly triggers a refresh
     * should implement this interface.
     */
    public interface OnRefreshListener {
        /**
         * Called when a swipe gesture triggers a refresh.
         *
         * @param isRefresh Refresh is true , load more is false
         */
        void onRefreshBegin(boolean isRefresh);

        /**
         * Called when refresh completed.
         *
         * @param isSuccessful refresh state
         */
        void onRefreshComplete(boolean isSuccessful);
    }


    /**
     * Classes that wish to be notified when the state changes should implement this interface.
     */
    public interface OnStateChangedListener {
        /**
         * Called when the state changes
         *
         * @param state The {@link State} that was changed
         */
        void onStateChanged(@State int state);
    }

    /**
     * Classes that wish to be notified when the views position changes should implement this
     * interface
     */
    public interface OnUIPositionChangedListener {
        /**
         * UI position changed
         *
         * @param status    {@link #SR_STATUS_INIT}, {@link #SR_STATUS_PREPARE},
         *                  {@link #SR_STATUS_REFRESHING},{@link #SR_STATUS_LOADING_MORE},{@link #SR_STATUS_COMPLETE}.
         * @param indicator @see {@link IIndicator}
         */
        void onChanged(byte status, IIndicator indicator);
    }

    /**
     * Classes that wish to be called when load more completed spring back to start position
     */
    public interface OnLoadMoreScrollCallback {
        /**
         * Called when load more completed spring back to start position, each move triggers a
         * callback once
         *
         * @param content The content view
         * @param deltaY  The scroll distance in Y-axis
         */
        void onScroll(View content, float deltaY);
    }

    public interface OnHookUIRefreshCompleteCallBack {
        @MainThread
        void onHook(RefreshCompleteHook hook);
    }

    /**
     * Classes that wish to be called when {@link SmoothRefreshLayout#setEnableScrollToBottomAutoLoadMore(boolean)}
     * has been set true and {@link SmoothRefreshLayout#isDisabledLoadMore()} not be true and
     * sure you need to customize the specified trigger rule
     */
    public interface OnPerformAutoLoadMoreCallBack {
        /**
         * Whether need trigger auto load more
         *
         * @param parent The frame
         * @param child  the child view
         * @return whether need trigger
         */
        boolean canAutoLoadMore(SmoothRefreshLayout parent, @Nullable View child);
    }

    @SuppressWarnings("unused")
    public static class LayoutParams extends MarginLayoutParams {
        private static final int[] LAYOUT_ATTRS = new int[]{android.R.attr.layout_gravity};
        private int mGravity = Gravity.TOP | Gravity.START;

        @SuppressWarnings("unused")
        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            final TypedArray a = c.obtainStyledAttributes(attrs, LAYOUT_ATTRS);
            mGravity = a.getInt(0, mGravity);
            a.recycle();
        }

        public LayoutParams(int width, int height, int gravity) {
            super(width, height);
            this.mGravity = gravity;
        }

        @SuppressWarnings({"unused"})
        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        @SuppressWarnings("unused")
        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        @SuppressWarnings("unused")
        public LayoutParams(LayoutParams source) {
            super(source);
            mGravity = source.mGravity;
        }

        public int getGravity() {
            return mGravity;
        }
    }

    public static class RefreshCompleteHook {
        private SmoothRefreshLayout mLayout;
        private OnHookUIRefreshCompleteCallBack mCallBack;

        public void onHookComplete() {
            if (mLayout != null) {
                if (SmoothRefreshLayout.sDebug) {
                    SRLog.i(SmoothRefreshLayout.TAG, "RefreshCompleteHook: onHookComplete()");
                }
                mLayout.performRefreshComplete(false);
            }
        }

        private void setHookCallBack(OnHookUIRefreshCompleteCallBack callBack) {
            mCallBack = callBack;
        }

        private void doHook() {
            if (mCallBack != null) {
                if (SmoothRefreshLayout.sDebug) {
                    SRLog.i(SmoothRefreshLayout.TAG, "RefreshCompleteHook: doHook()");
                }
                mCallBack.onHook(this);
            }
        }
    }

    /**
     * Delayed completion of loading
     */
    private static class DelayToRefreshComplete implements Runnable {
        private WeakReference<SmoothRefreshLayout> mLayoutWeakRf;

        private DelayToRefreshComplete(SmoothRefreshLayout layout) {
            mLayoutWeakRf = new WeakReference<>(layout);
        }

        @Override
        public void run() {
            if (mLayoutWeakRf.get() != null) {
                if (SmoothRefreshLayout.sDebug) {
                    SRLog.i(SmoothRefreshLayout.TAG, "DelayToRefreshComplete: run()");
                }
                mLayoutWeakRf.get().performRefreshComplete(true);
            }
        }
    }

    /**
     * Support over Scroll feature
     * The Over Scroll checker
     */
    private class OverScrollChecker implements Runnable {
        private final int mMaxDistance;
        private Scroller mScroller;
        private int mDuration = 0;
        private boolean mScrolling = false;
        private boolean mClamped = false;
        private boolean mFling = false;

        private OverScrollChecker() {
            DisplayMetrics dm = SmoothRefreshLayout.this.getContext().getResources()
                    .getDisplayMetrics();
            mMaxDistance = dm.heightPixels / 8;
            mScroller = new Scroller(SmoothRefreshLayout.this.getContext(),
                    new LinearInterpolator(), false);
        }

        private void fling(float vy) {
            destroy();
            mFling = true;
            mScroller.fling(0, 0, 0, (int) vy, Integer.MIN_VALUE, Integer.MAX_VALUE,
                    Integer.MIN_VALUE, Integer.MAX_VALUE);
            final int finalY = mScroller.getFinalY();
            final int duration = mScroller.getDuration();
            if (SmoothRefreshLayout.sDebug) {
                SRLog.d(SmoothRefreshLayout.TAG, "OverScrollChecker: fling(): vy: %s, finalY: %s," +
                        " duration: %s", vy, finalY, duration);
            }
            mScroller.startScroll(0, 0, 0, finalY, duration);
        }

        private void reset() {
            mFling = false;
            if (SmoothRefreshLayout.sDebug) {
                SRLog.i(SmoothRefreshLayout.TAG, "OverScrollChecker: reset()");
            }
            SmoothRefreshLayout.this.removeCallbacks(this);
            mScroller.forceFinished(true);
        }

        private void destroy() {
            reset();
            mScrolling = false;
            mClamped = false;
            mDuration = 0;
            SmoothRefreshLayout.this.resetScrollerInterpolator();
            if (SmoothRefreshLayout.sDebug) {
                SRLog.i(SmoothRefreshLayout.TAG, "OverScrollChecker: destroy()");
            }
        }

        private void abortIfWorking() {
            if (SmoothRefreshLayout.sDebug) {
                SRLog.d(SmoothRefreshLayout.TAG, "OverScrollChecker: abortIfWorking(): scrolling: %s",
                        mScrolling);
            }
            if (mScrolling) {
                destroy();
            }
        }

        private void computeScrollOffset() {
            if (mFling && mScroller.computeScrollOffset()) {
                mFling = true;
                SmoothRefreshLayout.this.removeCallbacks(this);
                SmoothRefreshLayout.this.postDelayed(this, 25);
            } else {
                mFling = false;
            }
            if (SmoothRefreshLayout.sDebug) {
                SRLog.d(SmoothRefreshLayout.TAG, "OverScrollChecker: computeScrollOffset(): fling: %s, " +
                        "finished: %s", mFling, mScroller.isFinished());
            }
        }

        @Override
        public void run() {
            if (!mFling)
                return;
            if (SmoothRefreshLayout.sDebug) {
                SRLog.d(SmoothRefreshLayout.TAG, "OverScrollChecker: run()");
            }
            if (!mScroller.isFinished()) {
                final int currY = mScroller.getCurrY();
                if (currY > 0 && SmoothRefreshLayout.this.isInStartPosition()
                        && !SmoothRefreshLayout.this.canChildScrollUp()
                        && !SmoothRefreshLayout.this.mScrollChecker.mIsRunning
                        && !(SmoothRefreshLayout.this.isEnabledPinRefreshViewWhileLoading()
                        && SmoothRefreshLayout.this.isRefreshing())) {
                    int to = calculateDistance(true);
                    if (SmoothRefreshLayout.this.isEnabledScrollToTopAutoRefresh()
                            && !SmoothRefreshLayout.this.isDisabledPerformRefresh()) {
                        int offsetToKeepHeaderWhileLoading = SmoothRefreshLayout.this.mIndicator
                                .getOffsetToKeepHeaderWhileLoading();
                        if (to > offsetToKeepHeaderWhileLoading) {
                            to = offsetToKeepHeaderWhileLoading;
                        }
                        mDuration = Math.max(mDuration, SmoothRefreshLayout.this
                                .getDurationToCloseFooter());
                        mClamped = false;
                    }
                    if (SmoothRefreshLayout.sDebug) {
                        SRLog.d(SmoothRefreshLayout.TAG, "OverScrollChecker: run(): to: %s, duration: %s",
                                to, mDuration);
                    }
                    SmoothRefreshLayout.this.mIndicator.setMovingStatus(IIndicator.MOVING_HEADER);
                    SmoothRefreshLayout.this.updateScrollerInterpolator(SmoothRefreshLayout
                            .this.mOverScrollInterpolator);
                    SmoothRefreshLayout.this.mScrollChecker.tryToScrollTo(to, mDuration);
                    //Add a buffer value
                    mDuration = mDuration + 88;
                    mScrolling = true;
                    reset();
                    return;
                } else if (currY < 0 && SmoothRefreshLayout.this.isInStartPosition()
                        && !SmoothRefreshLayout.this.canChildScrollDown()
                        && !SmoothRefreshLayout.this.mScrollChecker.mIsRunning
                        && !(SmoothRefreshLayout.this.isEnabledPinRefreshViewWhileLoading()
                        && SmoothRefreshLayout.this.isLoadingMore())) {
                    int to = calculateDistance(false);
                    if (SmoothRefreshLayout.this.isEnabledScrollToBottomAutoLoadMore()
                            && !SmoothRefreshLayout.this.isDisabledPerformLoadMore()) {
                        int offsetToKeepFooterWhileLoading = SmoothRefreshLayout.this.mIndicator
                                .getOffsetToKeepFooterWhileLoading();
                        if (to > offsetToKeepFooterWhileLoading) {
                            to = offsetToKeepFooterWhileLoading;
                        }
                        mDuration = Math.max(mDuration, SmoothRefreshLayout.this
                                .getDurationToCloseFooter());
                        mClamped = false;
                    }
                    if (SmoothRefreshLayout.sDebug) {
                        SRLog.d(SmoothRefreshLayout.TAG, "OverScrollChecker: run(): to: %s, duration: %s",
                                -to, mDuration);
                    }
                    SmoothRefreshLayout.this.mIndicator.setMovingStatus(IIndicator.MOVING_FOOTER);
                    SmoothRefreshLayout.this.updateScrollerInterpolator(SmoothRefreshLayout
                            .this.mOverScrollInterpolator);
                    SmoothRefreshLayout.this.mScrollChecker.tryToScrollTo(to, mDuration);
                    //Add a buffer value
                    mDuration = mDuration + 88;
                    mScrolling = true;
                    reset();
                    return;
                }
            }
            mScrolling = false;
            mClamped = false;
        }


        private int calculateDistance(boolean isMovingHeader) {
            int to;
            if (isMovingHeader) {
                to = Math.round(mScroller.getFinalY() - mScroller.getCurrY());
            } else {
                to = Math.round(mScroller.getCurrY() - mScroller.getFinalY());
            }
            mDuration = Math.round((mScroller.getDuration() - mScroller.timePassed()) *
                    SmoothRefreshLayout.this.mOverScrollDurationRatio);
            if (SmoothRefreshLayout.sDebug) {
                SRLog.d(SmoothRefreshLayout.TAG, "OverScrollChecker: calculateDistance(): " +
                        "originalDuration: %s", mDuration);
            }
            final int optimizedDistance;
            if (mDuration > SmoothRefreshLayout.this.mMaxOverScrollDuration * 1.5f) {
                mDuration = (int) Math.pow(mDuration, .76f);
                optimizedDistance = mMaxDistance;
            } else if (mDuration > SmoothRefreshLayout.this.mMaxOverScrollDuration) {
                mDuration = SmoothRefreshLayout.this.mMaxOverScrollDuration;
                optimizedDistance = mMaxDistance;
            } else if (mDuration > SmoothRefreshLayout.this.mMinOverScrollDuration) {
                optimizedDistance = Math.round(mMaxDistance * .6f /
                        (SmoothRefreshLayout.this.mMaxOverScrollDuration) * mDuration);
            } else {
                optimizedDistance = Math.round(mMaxDistance * .5f /
                        (SmoothRefreshLayout.this.mMaxOverScrollDuration) * mDuration);
                mDuration = SmoothRefreshLayout.this.mMinOverScrollDuration;
            }
            final float maxViewDistance;
            final int viewHeight;
            if (isMovingHeader) {
                maxViewDistance = SmoothRefreshLayout.this.mIndicator
                        .getCanMoveTheMaxDistanceOfHeader();
                viewHeight = SmoothRefreshLayout.this.getHeaderHeight();
            } else {
                maxViewDistance = SmoothRefreshLayout.this.mIndicator
                        .getCanMoveTheMaxDistanceOfFooter();
                viewHeight = SmoothRefreshLayout.this.getFooterHeight();
            }
            final int maxDistance = viewHeight > 0
                    ? Math.min(viewHeight, optimizedDistance) : optimizedDistance;
            to = to > maxDistance ? maxDistance : to;
            if (maxViewDistance > 0 && to > maxViewDistance) {
                to = Math.round(maxViewDistance);
            }
            if (SmoothRefreshLayout.sDebug) {
                SRLog.d(SmoothRefreshLayout.TAG, "OverScrollChecker: calculateDistance(): " +
                                "isMovingHeader: %s, duration: %s, optimizedDistance: %s, " +
                                "maxViewDistance: %s, maxDistance:%s, viewHeight: %s, to: %s",
                        isMovingHeader, mDuration, optimizedDistance, maxViewDistance, maxDistance,
                        viewHeight, to);
            }
            mClamped = true;
            return to;
        }
    }

    private class ScrollChecker implements Runnable {
        private int mLastY;
        private int mLastStart;
        private int mLastTo;
        private boolean mIsRunning = false;
        private Scroller mScroller;
        private Interpolator mInterpolator;
        private Field mInterpolatorField;

        private ScrollChecker() {
            mInterpolator = SmoothRefreshLayout.this.mSpringInterpolator;
            mScroller = new Scroller(SmoothRefreshLayout.this.getContext(), mInterpolator);
            try {
                mInterpolatorField = Scroller.class.getDeclaredField("mInterpolator");
                mInterpolatorField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                //ignore exception
            } catch (SecurityException e) {
                //ignore exception
            }
        }

        @Override
        public void run() {
            boolean finished = !mScroller.computeScrollOffset() || mScroller.isFinished();
            int curY = mScroller.getCurrY();
            int deltaY = curY - mLastY;
            if (SmoothRefreshLayout.sDebug) {
                SRLog.d(SmoothRefreshLayout.TAG,
                        "ScrollChecker: run(): finished: %s, start: %s, to: %s, currentPos: %s, " +
                                "currentY:%s, last: %s, delta: %s",
                        finished, mLastStart, mLastTo, mIndicator.getCurrentPosY(), curY,
                        mLastY, deltaY);
            }
            if (!finished) {
                mLastY = curY;
                if (SmoothRefreshLayout.this.isMovingHeader()) {
                    SmoothRefreshLayout.this.moveHeaderPos(deltaY);
                } else if (SmoothRefreshLayout.this.isMovingFooter()) {
                    SmoothRefreshLayout.this.moveFooterPos(-deltaY);
                }
                ViewCompat.postOnAnimation(SmoothRefreshLayout.this, this);
            } else {
                if (!SmoothRefreshLayout.this.canSpringBack()) {
                    checkInStartPosition();
                    reset(true);
                    SmoothRefreshLayout.this.onRelease(0);
                }
            }
        }

        private void updateInterpolator(Interpolator interpolator) {
            if (SmoothRefreshLayout.sDebug) {
                SRLog.d(SmoothRefreshLayout.TAG, "ScrollChecker: updateInterpolator()");
            }
            if (mInterpolator == interpolator)
                return;
            mInterpolator = interpolator;
            if (mIsRunning) {
                int timePassed = mScroller.timePassed();
                int duration = mScroller.getDuration();
                reset(false);
                mLastStart = mIndicator.getCurrentPosY();
                int distance = mLastTo - mLastStart;
                mScroller = new Scroller(getContext(), interpolator);
                mScroller.startScroll(0, 0, 0, distance, duration - timePassed);
                ViewCompat.postOnAnimation(SmoothRefreshLayout.this, this);
            } else {
                reset(false);
                reflectInterpolator(interpolator);
            }
        }

        private void reflectInterpolator(Interpolator interpolator) {
            if (mInterpolatorField == null)
                mScroller = new Scroller(getContext(), interpolator);
            else
                try {
                    if (!mInterpolatorField.isAccessible())
                        mInterpolatorField.setAccessible(true);
                    mInterpolatorField.set(mScroller, interpolator);
                } catch (IllegalAccessException e) {
                    mScroller = new Scroller(getContext(), interpolator);
                } catch (SecurityException e) {
                    mScroller = new Scroller(getContext(), interpolator);
                }
        }

        private void checkInStartPosition() {
            //It should have scrolled to the specified location, but it has not scrolled
            if (mLastTo == IIndicator.DEFAULT_START_POS
                    && !SmoothRefreshLayout.this.mIndicator.isInStartPosition()) {
                int currentPos = SmoothRefreshLayout.this.mIndicator.getCurrentPosY();
                int deltaY = IIndicator.DEFAULT_START_POS - currentPos;
                if (SmoothRefreshLayout.sDebug) {
                    SRLog.d(SmoothRefreshLayout.TAG, "ScrollChecker: checkInStartPosition(): deltaY: %s",
                            deltaY);
                }
                if (SmoothRefreshLayout.this.isMovingHeader()) {
                    SmoothRefreshLayout.this.moveHeaderPos(deltaY);
                } else if (SmoothRefreshLayout.this.isMovingFooter()) {
                    SmoothRefreshLayout.this.moveFooterPos(-deltaY);
                }
            }
        }

        private void reset(boolean stopOverScrollCheck) {
            if (SmoothRefreshLayout.sDebug) {
                SRLog.i(SmoothRefreshLayout.TAG, "ScrollChecker: reset()");
            }
            mIsRunning = false;
            mLastY = 0;
            if (stopOverScrollCheck)
                mOverScrollChecker.abortIfWorking();
            SmoothRefreshLayout.this.removeCallbacks(this);
        }

        private void destroy() {
            if (SmoothRefreshLayout.sDebug) {
                SRLog.i(SmoothRefreshLayout.TAG, "ScrollChecker: destroy()");
            }
            reset(true);
            mScroller.forceFinished(true);
        }

        private void abortIfWorking() {
            if (SmoothRefreshLayout.sDebug) {
                SRLog.i(SmoothRefreshLayout.TAG, "ScrollChecker: abortIfWorking()");
            }
            if (mIsRunning) {
                mScroller.forceFinished(true);
                reset(true);
            }
        }

        private void tryToScrollTo(int to, int duration) {
            if (SmoothRefreshLayout.sDebug) {
                SRLog.i(SmoothRefreshLayout.TAG, "ScrollChecker: tryToScrollTo()");
            }
            mLastStart = SmoothRefreshLayout.this.mIndicator.getCurrentPosY();
            if (SmoothRefreshLayout.this.mIndicator.isAlreadyHere(to)) {
                SmoothRefreshLayout.this.mOverScrollChecker.abortIfWorking();
                return;
            }
            mLastTo = to;
            int distance = to - mLastStart;
            mLastY = 0;
            if (SmoothRefreshLayout.sDebug) {
                SRLog.d(SmoothRefreshLayout.TAG, "ScrollChecker: tryToScrollTo(): start: %s, to:%s, duration:%s",
                        mLastStart, to, duration);
            }
            mScroller.forceFinished(true);
            if (duration > 0) {
                mScroller.startScroll(0, 0, 0, distance, duration);
                SmoothRefreshLayout.this.removeCallbacks(this);
                SmoothRefreshLayout.this.post(this);
                mIsRunning = true;
            } else {
                if (SmoothRefreshLayout.this.isMovingHeader()) {
                    SmoothRefreshLayout.this.moveHeaderPos(distance);
                } else if (SmoothRefreshLayout.this.isMovingFooter()) {
                    SmoothRefreshLayout.this.moveFooterPos(-distance);
                }
                destroy();
            }
        }
    }
}

package com.ethan.viewrecorder.utils;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * 屏幕相关的工具
 *
 * @author zhuziqiang
 * @since 2018/5/7
 */
public class ScreenUtil {

    /**
     * 四舍五入
     */
    private static final float DOT_FIVE = 0.5f;

    public static int getScreenWidth(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return dm.widthPixels;
    }

    public static int getScreenHeight(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return dm.heightPixels;
    }

    /**
     * dip转换成px
     *
     * @param context Context
     * @param dip     dip Value
     *
     * @return 换算后的px值
     */
    public static int dip2px(Context context, float dip) {
        float density = getDensity(context);
        return (int) (dip * density + DOT_FIVE);
    }

    /**
     * px转换成dip
     *
     * @param context Context
     * @param px      px Value
     *
     * @return 换算后的dip值
     */
    public static int px2dip(Context context, float px) {
        float density = getDensity(context);
        return (int) (px / density + DOT_FIVE);
    }

    /**
     * 得到显示密度
     *
     * @param context Context
     *
     * @return 密度
     */
    public static float getDensity(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return dm.density;
    }

    public static int getStatusBarHeight(Context context) {
        Resources res = context.getResources();
        int result = 0;
        int resourceId = res.getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = res.getDimensionPixelSize(resourceId);
        }
        return result;
    }

    /**
     * 判断是否是全面屏
     */
    private volatile static boolean mHasCheckAllScreen;
    private volatile static boolean mIsAllScreenDevice;

    public static boolean isAllScreenDevice(Context context) {
        if (mHasCheckAllScreen) {
            return mIsAllScreenDevice;
        }
        mHasCheckAllScreen = true;
        mIsAllScreenDevice = false;
        // 低于 API 21的，都不会是全面屏
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        int width, height;
        width = getScreenWidth(context);
        height = getScreenHeight(context);
        if (width != 0 && height / width >= 1.97f) {
            mIsAllScreenDevice = true;
        }

        return mIsAllScreenDevice;
    }

    public static int getScreenWidth2(Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(metrics);
        return metrics.widthPixels;
    }
}

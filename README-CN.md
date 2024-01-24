# ViewRecorder

Android View 级别的屏幕区域录制方案（支持 SurfaceView 和 TextureView）

ViewRecorder 非常容易使用，因为只扩展了一个额外的 API，用于扩展 MediaRecord 来设置或切换录制视图。此外，还有一个名为 SurfaceMediaRecorder 的类，它直接扩展了 MediaRecord。视频帧是根据帧率周期性组成的，每一帧组成都有接口，你可以根据自己的需要进行定制。同时，你可以自定义 Android Looper，分担主线程压力。

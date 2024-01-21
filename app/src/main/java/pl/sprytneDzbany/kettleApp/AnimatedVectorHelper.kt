package pl.sprytneDzbany.kettleApp

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.ImageView
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class AnimatedVectorHelper(context: Activity, animatedVectorPointer: Int, holder: ImageView) {
    @SuppressLint("StaticFieldLeak")
    private var animated: AnimatedVectorDrawableCompat =
        AnimatedVectorDrawableCompat.create(context, animatedVectorPointer)!!

    private val sleeper = Object()
    private val TAG = "AnimatedVectorHelper"

    init {
        holder.setImageDrawable(animated)
    }

    fun startLoop(holder: ImageView){
        animated.registerAnimationCallback(object : Animatable2Compat.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable?) {
                holder.post {
                    animated.start()
                }
            }
        })
        animated.start()
    }

    fun stopLoop(holder: ImageView){
        animated.registerAnimationCallback(object : Animatable2Compat.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable?) {
                holder.post { animated.stop() }
            }
        })
    }
}
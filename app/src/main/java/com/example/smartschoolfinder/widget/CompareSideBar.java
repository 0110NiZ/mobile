package com.example.smartschoolfinder.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.example.smartschoolfinder.R;

public class CompareSideBar extends View {
    private static final String[] LETTERS = {
            "A","B","C","D","E","F","G","H","I","J","K","L","M",
            "N","O","P","Q","R","S","T","U","V","W","X","Y","Z","#"
    };

    private final Paint normalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int currentIndex = -1;
    private SideBar.OnLetterChangedListener listener;

    public CompareSideBar(Context context) {
        super(context);
        init();
    }

    public CompareSideBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CompareSideBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        normalPaint.setColor(ContextCompat.getColor(getContext(), R.color.compare_index_normal));
        normalPaint.setTextSize(22f);
        normalPaint.setTextAlign(Paint.Align.CENTER);

        selectedPaint.setColor(ContextCompat.getColor(getContext(), R.color.compare_index_selected));
        selectedPaint.setFakeBoldText(true);
        selectedPaint.setTextSize(24f);
        selectedPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setOnLetterChangedListener(SideBar.OnLetterChangedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        float singleHeight = height * 1f / LETTERS.length;
        for (int i = 0; i < LETTERS.length; i++) {
            Paint paint = i == currentIndex ? selectedPaint : normalPaint;
            float x = width / 2f;
            float y = singleHeight * i + singleHeight * 0.75f;
            canvas.drawText(LETTERS[i], x, y, paint);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        float y = event.getY();
        int index = (int) (y / getHeight() * LETTERS.length);
        if (index < 0) index = 0;
        if (index >= LETTERS.length) index = LETTERS.length - 1;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                if (index != currentIndex) {
                    currentIndex = index;
                    if (listener != null) listener.onLetterChanged(LETTERS[index]);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                currentIndex = -1;
                if (listener != null) listener.onTouchReleased();
                invalidate();
                return true;
            default:
                return super.dispatchTouchEvent(event);
        }
    }
}

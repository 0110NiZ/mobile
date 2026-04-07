package com.example.smartschoolfinder.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class SideBar extends View {
    private static final String[] LETTERS = {
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#"
    };

    private final Paint normalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int currentIndex = -1;
    private OnLetterChangedListener listener;

    public interface OnLetterChangedListener {
        void onLetterChanged(String letter);
        void onTouchReleased();
    }

    public SideBar(Context context) {
        super(context);
        init();
    }

    public SideBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SideBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        normalPaint.setColor(Color.parseColor("#8A8AA0"));
        normalPaint.setTextSize(24f);
        normalPaint.setTextAlign(Paint.Align.CENTER);

        selectedPaint.setColor(Color.parseColor("#5A3E99"));
        selectedPaint.setFakeBoldText(true);
        selectedPaint.setTextSize(26f);
        selectedPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setOnLetterChangedListener(OnLetterChangedListener listener) {
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

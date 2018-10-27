package dev.dworks.apps.anexplorer.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import dev.dworks.apps.anexplorer.R;

public class RoundedSquareImage extends View {
    
    private int centerY;
    private int centerX;
    private int outerRadius;
    private Paint circlePaint;
    private RectF rect;
    private int defaultColor = Color.GRAY;
    
    public RoundedSquareImage(Context context) {
        super(context);
        init(context, null);
    }
    
    public RoundedSquareImage(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }
    
    public RoundedSquareImage(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRoundRect(rect, 16,16,circlePaint);
        super.onDraw(canvas);
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect = new RectF(0, 0, w, w);
        centerX = w / 2;
        centerY = h / 2;
        outerRadius = Math.min(w, h) / 2;
    }
    
    public void setColor(int color) {
        this.defaultColor = color;
        circlePaint.setColor(defaultColor);
        
        this.invalidate();
    }
    
    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size = width > height ? height : width;
        setMeasuredDimension(size, size);
    }
    
    private void init(Context context, AttributeSet attrs) {
        //this.setScaleType(ScaleType.CENTER_INSIDE);
        
        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setStyle(Paint.Style.FILL);
        
        int color = defaultColor;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.View);
            color = a.getColor(R.styleable.View_background, color);
            a.recycle();
        }
        
        setColor(color);
    }
    
    @Override
    public void setBackgroundColor(int color) {
        setColor(color);
    }
}
package dev.dworks.apps.anexplorer.ui;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class ChartView extends View {
    private Paint mCircleYellow;
    private Paint mCircleGray;
    
    private float mRadius;
    private RectF mArcBounds = new RectF();
    public ChartView(Context context) {
        super(context);
        init();
    }
    
    public ChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public ChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    public ChartView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }
    
    public void init() {
    
    
    }
}

package com.genymobile.scrcpy;

import android.os.SystemClock;
import android.view.MotionEvent;

/**
 * Union of all supported event types, identified by their {@code type}.
 */
public final class ControlMessage {

    public static final int TYPE_INJECT_KEYCODE        = 0;
    public static final int TYPE_INJECT_TEXT           = 1;
    public static final int TYPE_INJECT_MOUSE_EVENT    = 2;
    public static final int TYPE_INJECT_TOUCH_EVENT    = 3;
    public static final int TYPE_INJECT_SCROLL_EVENT   = 4;
    public static final int TYPE_COMMAND               = 5;
    public static final int TYPE_SET_CLIPBOARD         = 6;
    public static final int TYPE_SET_SCREEN_POWER_MODE = 7;

    public static final int COMMAND_BACK_OR_SCREEN_ON           = 0;
    public static final int COMMAND_EXPAND_NOTIFICATION_PANEL   = 1;
    public static final int COMMAND_COLLAPSE_NOTIFICATION_PANEL = 2;
    public static final int COMMAND_QUIT                        = 3;
    public static final int COMMAND_TO_PORTRAIT                 = 4;
    public static final int COMMAND_TO_LANDSCAPE                = 5;
    public static final int COMMAND_PING                        = 6;
    public static final int COMMAND_GET_CLIPBOARD               = 7;

    public static final int MAX_FINGERS = 10;

    private int type;
    private String text;
    private int metaState; // KeyEvent.META_*
    private int action;    // KeyEvent.ACTION_* or MotionEvent.ACTION_* or COMMAND_* or POWER_MODE_*
    private int keycode;   // KeyEvent.KEYCODE_*
    private int buttons;   // MotionEvent.BUTTON_*
    private Position position;
    private int hScroll;
    private int vScroll;
    private int fingerId;
    private final long timestamp;

    private static long referenceTime  = 0;
    private static long lastEventLocal = 0;
    private static long lastEvent      = 0;

    private ControlMessage() {
        this.timestamp = SystemClock.uptimeMillis();
    }

    private ControlMessage(long t, boolean startEvent) {
        /*
            t is actually 32-bit unsigned value, and it wraps in 49.7 days.
            Here it is presumed that the server will never run continuously
            for so long time.
        */
        long nowLocal = SystemClock.uptimeMillis();
        long now      = referenceTime+t;

        /*
            It is important to reproduce real delays between events
            relative to ACTION_DOWN. TCP may deliver several events
            at once. Providing only relative to reference timestamps
            does not help much.
        */
        if (lastEventLocal > 0 && !startEvent) {
            long delay = (now-lastEvent)-(nowLocal-lastEventLocal);
            if (delay > 0) {
                if (delay > 5)
                    SystemClock.sleep(delay > 50 ? 50 : delay-5);
                nowLocal = SystemClock.uptimeMillis();
            }
            else
                nowLocal += delay;
//            Ln.i("Delay="+delay);
        }

        this.timestamp = nowLocal;
        lastEventLocal = nowLocal;
        lastEvent      = now;

    }

    public static ControlMessage createInjectKeycode(int action, int keycode, int metaState) {
        ControlMessage event = new ControlMessage();
        event.type      = TYPE_INJECT_KEYCODE;
        event.action    = action;
        event.keycode   = keycode;
        event.metaState = metaState;
        return event;
    }

    public static ControlMessage createInjectText(String text) {
        ControlMessage event = new ControlMessage();
        event.type = TYPE_INJECT_TEXT;
        event.text = text;
        return event;
    }

    public static ControlMessage createInjectMouseEvent(int action, int buttons, Position position) {
        ControlMessage event = new ControlMessage();
        event.type     = TYPE_INJECT_MOUSE_EVENT;
        event.action   = action;
        event.buttons  = buttons;
        event.position = position;
        return event;
    }

    public static ControlMessage createInjectTouchEvent(int action, int fingerId, Position position, long timestamp) {
        if (fingerId < 0 || fingerId >= MAX_FINGERS)
            fingerId = 0;
        ControlMessage event = new ControlMessage(timestamp, action == MotionEvent.ACTION_DOWN);
        event.type     = TYPE_INJECT_TOUCH_EVENT;
        event.action   = action;
        event.position = position;
        event.fingerId = fingerId;
        return event;
    }

    public static ControlMessage createInjectScrollEvent(Position position, int hScroll, int vScroll) {
        ControlMessage event = new ControlMessage();
        event.type     = TYPE_INJECT_SCROLL_EVENT;
        event.position = position;
        event.hScroll  = hScroll;
        event.vScroll  = vScroll;
        return event;
    }

    public static ControlMessage createSetClipboard(String text) {
        ControlMessage event = new ControlMessage();
        event.type = TYPE_SET_CLIPBOARD;
        event.text = text;
        return event;
    }

    /**
     * @param mode one of the {@code Device.SCREEN_POWER_MODE_*} constants
     */
    public static ControlMessage createSetScreenPowerMode(int mode) {
        ControlMessage event = new ControlMessage();
        event.type   = TYPE_SET_SCREEN_POWER_MODE;
        event.action = mode;
        return event;
    }

    public static ControlMessage createEmpty(int type) {
        ControlMessage event = new ControlMessage();
        event.type = type;
        return event;
    }

    public static ControlMessage createCommandEvent(int action) {
        if (referenceTime == 0 && action == COMMAND_PING)
           referenceTime = SystemClock.uptimeMillis();

        ControlMessage event = new ControlMessage();
        event.type   = TYPE_COMMAND;
        event.action = action;
        return event;
    }

    public String getText() { return text; }
    public Position getPosition() { return position; }
    public int  getType()      { return type; }
    public int  getMetaState() { return metaState; }
    public int  getAction()    { return action; }
    public int  getKeycode()   { return keycode; }
    public int  getButtons()   { return buttons; }
    public int  getHScroll()   { return hScroll; }
    public int  getVScroll()   { return vScroll; }
    public int  getFingerId()  { return fingerId; }
    public long getTime()      { return timestamp; }
}

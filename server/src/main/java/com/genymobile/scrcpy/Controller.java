package com.genymobile.scrcpy;

import com.genymobile.scrcpy.wrappers.InputManager;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class Controller {

    private boolean Running = true;
    private final Device device;
    private final DesktopConnection connection;
    private final DeviceMessageSender sender;

    private final KeyCharacterMap charMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);

    private long lastMouseDown;
    private long lastTouchDown;
    private final MotionEvent.PointerProperties[] pointerProperties = {new MotionEvent.PointerProperties()};
    private final MotionEvent.PointerCoords[] pointerCoords = {new MotionEvent.PointerCoords()};

    private final MotionEvent.PointerProperties[] touchPointerProperties = new MotionEvent.PointerProperties[ControlMessage.MAX_FINGERS];
    private final MotionEvent.PointerCoords[] touchPointerCoords = new MotionEvent.PointerCoords[ControlMessage.MAX_FINGERS];
    private final Point[] touchPoints = new Point[ControlMessage.MAX_FINGERS];

    private long lastEventTime = SystemClock.uptimeMillis();

    private final IME ime = new IME();

    public Controller(Device device, DesktopConnection connection) {
        this.device     = device;
        this.connection = connection;
        this.sender     = new DeviceMessageSender(connection);
        initPointers();
    }

    private void initPointers() {
        MotionEvent.PointerProperties props = pointerProperties[0];
        props.id = 0;
        props.toolType = MotionEvent.TOOL_TYPE_FINGER;

        for (int i=0; i < touchPointerProperties.length; i++) {
            MotionEvent.PointerProperties p = new MotionEvent.PointerProperties();
            p.id       = 0;
            p.toolType = MotionEvent.TOOL_TYPE_FINGER;
            touchPointerProperties[i] = p;
        }

        MotionEvent.PointerCoords coords = pointerCoords[0];
        coords.orientation = 0;
        coords.pressure = 1;
        coords.size = 1;

        for (int i=0; i < touchPointerCoords.length; i++) {
            MotionEvent.PointerCoords c = new MotionEvent.PointerCoords();
            c.orientation = 0;
            c.pressure    = 1;
            c.size        = 1;
            touchPointerCoords[i] = c;
        }
    }

    private void setPointerCoords(Point point) {
        MotionEvent.PointerCoords coords = pointerCoords[0];
        coords.x = point.getX();
        coords.y = point.getY();
    }

    private void setScroll(int hScroll, int vScroll) {
        MotionEvent.PointerCoords coords = pointerCoords[0];
        coords.setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll);
        coords.setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public void control() {
        // on start, power on the device
        if (turnScreenOn()) {
            // dirty hack
            // After POWER is injected, the device is powered on asynchronously.
            // To turn the device screen off while mirroring, the client will send a message that
            // would be handled before the device is actually powered on, so its effect would
            // be "canceled" once the device is turned back on.
            // Adding this delay prevents to handle the message before the device is actually
            // powered on.
            SystemClock.sleep(500);
        }

        final Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                final long last = lastEventTime;
                final long now  = SystemClock.uptimeMillis();
                if (now - last > 6000) {
                    Ln.i("Inactivity timeout, quit");
                    Running = false;
                    connection.close();
                }
            }
        }, 10000, 1000);

        try {
            while (Running) {
                handleEvent();
            }
        } catch (Exception e) {
        }

        timer.cancel();
        ime.Finish();
        sender.stop();
    }

    public DeviceMessageSender getSender() {
        return sender;
    }

    private void handleEvent() throws IOException {
        ControlMessage msg = connection.receiveControlMessage();
        lastEventTime = SystemClock.uptimeMillis();
        switch (msg.getType()) {
            case ControlMessage.TYPE_INJECT_KEYCODE:
                injectKeycode(msg.getAction(), msg.getKeycode(), msg.getMetaState(), msg.getTime());
                break;
            case ControlMessage.TYPE_INJECT_TEXT:
                injectText(msg.getText());
                break;
            case ControlMessage.TYPE_INJECT_MOUSE_EVENT:
                injectMouse(msg.getAction(), msg.getButtons(), msg.getPosition(), msg.getTime());
                break;
            case ControlMessage.TYPE_INJECT_TOUCH_EVENT:
                injectTouch(msg.getAction(), msg.getPosition(), msg.getFingerId(), msg.getTime());
                break;
            case ControlMessage.TYPE_INJECT_SCROLL_EVENT:
                injectScroll(msg.getPosition(), msg.getHScroll(), msg.getVScroll(), msg.getTime());
                break;
            case ControlMessage.TYPE_COMMAND:
                executeCommand(msg.getAction());
                break;
            case ControlMessage.TYPE_SET_CLIPBOARD:
                device.setClipboardText(msg.getText());
                break;
            case ControlMessage.TYPE_SET_SCREEN_POWER_MODE:
                device.setScreenPowerMode(msg.getAction());
                break;
            default:
                // do nothing
        }
    }

    private boolean injectKeycode(int action, int keycode, int metaState, long now) {
        return injectKeyEvent(action, keycode, 0, metaState, now);
    }

    private boolean injectChar(char c) {
        String decomposed = KeyComposition.decompose(c);
        char[] chars = decomposed != null ? decomposed.toCharArray() : new char[]{c};
        KeyEvent[] events = charMap.getEvents(chars);
        if (events == null) {
            return false;
        }
        for (KeyEvent event : events) {
            if (!injectEvent(event)) {
                return false;
            }
        }
        return true;
    }

    private int injectText(String text) {
        if (ime.send(text)) return text.length();

        int successCount = 0;
        for (char c : text.toCharArray()) {
            if (!injectChar(c))
                Ln.w("Could not inject char u+" + String.format("%04x", (int) c));
            else
                successCount++;
        }
        return successCount;
    }

    private boolean injectMouse(int action, int buttons, Position position, long now) {
        if (action == MotionEvent.ACTION_DOWN) {
            lastMouseDown = now;
        }
        Point point = device.getPhysicalPoint(position);
        if (point == null) {
            // ignore event
            return false;
        }
        setPointerCoords(point);
        MotionEvent event = MotionEvent.obtain(lastMouseDown, now, action, 1, pointerProperties, pointerCoords, 0, buttons, 1f, 1f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0);
        return injectEvent(event);
    }

    private boolean injectTouch(int action, Position position, int fingerId, long now) {
        if (action == MotionEvent.ACTION_DOWN) {
            lastTouchDown = now;
        }

        touchPoints[fingerId] = device.getPhysicalPoint(position);
        if (touchPoints[fingerId] == null) {
            // ignore event
            return false;
        }

        int pointerCount = 0;
        for (int i=0; i < touchPoints.length; i++) {
            if (touchPoints[i] == null) continue;
            touchPointerProperties[pointerCount].id = i;
            touchPointerCoords[pointerCount].x = touchPoints[i].getX();
            touchPointerCoords[pointerCount].y = touchPoints[i].getY();
            pointerCount++;
        }

        MotionEvent event = MotionEvent.obtain(lastTouchDown, now, action | (fingerId << 8), pointerCount, touchPointerProperties, touchPointerCoords, 0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0);
        boolean result = injectEvent(event);

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
            touchPoints[fingerId] = null;
        else if (action == MotionEvent.ACTION_CANCEL) {
            // Reset the gesture
            for (int i=0; i < touchPoints.length; i++)
                touchPoints[i] = null;
        }

        return result;
    }

    private boolean injectScroll(Position position, int hScroll, int vScroll, long now) {
        Point point = device.getPhysicalPoint(position);
        if (point == null) {
            // ignore event
            return false;
        }
        setPointerCoords(point);
        setScroll(hScroll, vScroll);
        MotionEvent event = MotionEvent.obtain(lastMouseDown, now, MotionEvent.ACTION_SCROLL, 1, pointerProperties, pointerCoords, 0, 0, 1f, 1f, 0,
                0, InputDevice.SOURCE_MOUSE, 0);
        return injectEvent(event);
    }

    private boolean injectKeyEvent(int action, int keyCode, int repeat, int metaState, long now) {
        KeyEvent event = new KeyEvent(now, now, action, keyCode, repeat, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                InputDevice.SOURCE_KEYBOARD);
        return injectEvent(event);
    }

    private boolean injectKeycode(int keyCode) {
        final long now = SystemClock.uptimeMillis();
        return injectKeyEvent(KeyEvent.ACTION_DOWN, keyCode, 0, 0, now)
                && injectKeyEvent(KeyEvent.ACTION_UP, keyCode, 0, 0, now);
    }

    private boolean injectEvent(InputEvent event) {
        return device.injectInputEvent(event, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    private boolean turnScreenOn() {
        return device.isScreenOn() ? false : injectKeycode(KeyEvent.KEYCODE_POWER);
    }

    public boolean turnScreenOff() {
        return device.isScreenOn() && injectKeycode(KeyEvent.KEYCODE_POWER);
    }

    private boolean pressBackOrTurnScreenOn() {
        int keycode = device.isScreenOn() ? KeyEvent.KEYCODE_BACK : KeyEvent.KEYCODE_POWER;
        return injectKeycode(keycode);
    }

    private boolean executeCommand(int action) {
        switch (action) {
            case ControlMessage.COMMAND_BACK_OR_SCREEN_ON:
                return pressBackOrTurnScreenOn();
            case ControlMessage.COMMAND_EXPAND_NOTIFICATION_PANEL:
                device.expandNotificationPanel();
                return true;
            case ControlMessage.COMMAND_COLLAPSE_NOTIFICATION_PANEL:
                device.collapsePanels();
                return true;
            case ControlMessage.COMMAND_QUIT:
                Running = false;
                Ln.i("Command QUIT received");
                return true;
            case ControlMessage.COMMAND_TO_PORTRAIT:
                DeviceControl.setPortrait();
                return true;
            case ControlMessage.COMMAND_TO_LANDSCAPE:
                DeviceControl.setLandscape();
                return true;
            case ControlMessage.COMMAND_PING:
                return true;
            case ControlMessage.COMMAND_GET_CLIPBOARD:
                String clipboardText = device.getClipboardText();
                sender.pushClipboardText(clipboardText);
                break;
            default:
                Ln.w("Unsupported command: " + action);
        }
        return false;
    }
}

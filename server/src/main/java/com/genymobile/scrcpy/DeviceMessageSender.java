package com.genymobile.scrcpy;

import java.io.IOException;

public final class DeviceMessageSender {

    private final DesktopConnection connection;

    private String clipboardText;
    private boolean running = true;

    public DeviceMessageSender(DesktopConnection connection) {
        this.connection = connection;
    }

    public synchronized void pushClipboardText(String text) {
        clipboardText = text;
        notify();
    }

    public void loop() throws IOException, InterruptedException {
        while (running) {
            String text;
            synchronized (this) {
                while (running && clipboardText == null) {
                    wait();
                }
                text = clipboardText;
                clipboardText = null;
            }
            if (text != null && !text.isEmpty()) {
                DeviceMessage event = DeviceMessage.createClipboard(text);
                connection.sendDeviceMessage(event);
            }
        }
    }

    public synchronized void stop() {
        running = false;
        notify();
    }
}

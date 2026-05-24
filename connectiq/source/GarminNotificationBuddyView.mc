using Toybox.Application as Application;
using Toybox.Graphics as Graphics;
using Toybox.Lang as Lang;
using Toybox.System as System;
using Toybox.WatchUi as WatchUi;

class GarminNotificationBuddyView extends WatchUi.View {

    function initialize() {
        View.initialize();
    }

    function onUpdate(dc as Dc) as Void {
        dc.clear();

        var width = dc.getWidth();
        var y = 8;

        dc.drawText(width / 2, y, Graphics.FONT_SMALL, "Buddy Inbox", Graphics.TEXT_JUSTIFY_CENTER);
        y += 20;

        var inbox = Application.Storage.getValue("buddyInbox");
        if (inbox == null || inbox.size() == 0) {
            dc.drawText(8, y, Graphics.FONT_XTINY, "No forwarded notifications yet.", Graphics.TEXT_JUSTIFY_LEFT);
            return;
        }

        for (var i = inbox.size() - 1; i >= 0; i -= 1) {
            var item = inbox[i];
            var source = item[:source] == null ? "App" : item[:source].toString();
            var title = item[:title] == null ? "Notification" : item[:title].toString();
            dc.drawText(8, y, Graphics.FONT_XTINY, source + ": " + title, Graphics.TEXT_JUSTIFY_LEFT);
            y += 18;

            if (y > dc.getHeight() - 14) {
                break;
            }
        }
    }
}

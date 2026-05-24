using Toybox.Application as Application;
using Toybox.Communications as Communications;
using Toybox.Lang as Lang;
using Toybox.WatchUi as WatchUi;

class GarminNotificationBuddyApp extends Application.AppBase {

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state as Dictionary?) as Void {
        Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
    }

    function getInitialView() as [Views] or [Views, InputDelegates] {
        return [ new GarminNotificationBuddyView(), new GarminNotificationBuddyDelegate() ];
    }

    function onStop(state as Dictionary?) as Void {
    }

    function onPhoneMessage(message as Dictionary?) as Void {
        if (message == null) {
            return;
        }

        var inbox = Application.Storage.getValue("buddyInbox");
        if (inbox == null) {
            inbox = [];
        }

        inbox.add({
            :title => message[:title],
            :source => message[:sourceApp],
            :body => message[:body],
            :timestamp => message[:timestamp]
        });

        while (inbox.size() > 8) {
            inbox.remove(0);
        }

        Application.Storage.setValue("buddyInbox", inbox);
        WatchUi.requestUpdate();
    }
}

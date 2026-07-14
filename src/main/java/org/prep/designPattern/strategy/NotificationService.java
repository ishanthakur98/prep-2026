package org.prep.designPattern.strategy;

public class NotificationService {
    

    private final NotificationSender notificationSender;

    public NotificationService(NotificationSender notificationSender){
        this.notificationSender = notificationSender;
    }

    public void notify(String recipient, String message) {
        notificationSender.send(recipient, message); 
    }

    public static void main(String[] args) {
        NotificationService emailNotification = new NotificationService(new EmailNotification());
        NotificationService smNotification = new NotificationService(new SmsNotification());

        emailNotification.notify("ishan","Hi ishan");
        smNotification.notify("neha", "Hi neha");
    }
}

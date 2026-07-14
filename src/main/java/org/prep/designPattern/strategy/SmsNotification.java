package org.prep.designPattern.strategy;

public class SmsNotification implements NotificationSender{

    @Override
    public void send(String to, String message) {
        // TODO Auto-generated method stub
        System.out.printf("%s sent sms message as %s",to,message);
    }
    
}

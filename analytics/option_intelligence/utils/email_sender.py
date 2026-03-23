import smtplib
from email.mime.text import MIMEText
from option_intelligence.config.email_config import EMAIL_CONFIG


class EmailSender:

    def send(self, subject, body):

        try:
            msg = MIMEText(body)
            msg["Subject"] = subject
            msg["From"] = EMAIL_CONFIG["sender_email"]
            msg["To"] = EMAIL_CONFIG["receiver_email"]

            server = smtplib.SMTP(
                EMAIL_CONFIG["smtp_server"],
                EMAIL_CONFIG["smtp_port"]
            )
            server.starttls()

            server.login(
                EMAIL_CONFIG["sender_email"],
                EMAIL_CONFIG["sender_password"]
            )

            server.sendmail(
                EMAIL_CONFIG["sender_email"],
                EMAIL_CONFIG["receiver_email"],
                msg.as_string()
            )

            server.quit()

            print("✅ Email sent successfully")

        except Exception as e:
            print("❌ Email error:", str(e))
            
package Core.Form;

import Forms.LoginForm;
import Helper.Injector;
import java.awt.Dimension;
import java.awt.Toolkit;
import javax.swing.JFrame;

public abstract class BaseForm extends JFrame {

  @Override
  public void addNotify() {
    super.addNotify(); // Calls the standard Swing internal logic
    this.InitLayout(); // Triggers the centering
  }

  public void PerformLogout() {
    this.dispose();

    LoginForm login = Injector.CreateLoginForm();

    login.setVisible(true);
  }

  protected void CenterFrame(JFrame frame) {
    Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
    frame.setLocation(
      dim.width / 2 - frame.getSize().width / 2,
      dim.height / 2 - frame.getSize().height / 2
    );
  }

  public void InitLayout() {
    this.pack();
    CenterFrame(this);
  }
}

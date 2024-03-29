package frc.robot;

import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;

import edu.wpi.first.wpilibj.SpeedControllerGroup;
import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj.GenericHID.Hand;
import edu.wpi.first.wpilibj.drive.DifferentialDrive;
import edu.wpi.first.networktables.*;

/**
 * The VM is configured to automatically run this class, and to call the
 * functions corresponding to each mode, as described in the TimedRobot
 * documentation. If you change the name of this class or the package after
 * creating this project, you must also update the build.gradle file in the
 * project.
 */
public class Robot extends TimedRobot {

  private static final String kDefaultAuto = "Default";
  private static final String kCustomAuto = "My Auto";
  private String m_autoSelected;
  private final SendableChooser<String> m_chooser = new SendableChooser<>();

  private final WPI_TalonSRX m_Left0 = new WPI_TalonSRX(5); // FL
  private final WPI_TalonSRX m_Left1 = new WPI_TalonSRX(3); // BL
  private final WPI_TalonSRX m_Right0 = new WPI_TalonSRX(5); // FR
  private final WPI_TalonSRX m_Right1 = new WPI_TalonSRX(8); // BR
  private SpeedControllerGroup m_LeftMotors = new SpeedControllerGroup(m_Left0, m_Left1);
  private SpeedControllerGroup m_RightMotors = new SpeedControllerGroup(m_Right0, m_Right1);
  private final DifferentialDrive m_Drive = new DifferentialDrive(m_LeftMotors, m_RightMotors);

  private final XboxController m_Controller = new XboxController(0);

  private boolean m_LimelightHasValidTarget = false;
  private double m_LimelightDriveCommand = 0.0;
  private double m_LimelightSteerCommand = 0.0;

  /**
   * This function is run when the robot is first started up and should be used
   * for any initialization code.
   */
  @Override
  public void robotInit() {
    m_chooser.setDefaultOption("Default Auto", kDefaultAuto);
    m_chooser.addOption("My Auto", kCustomAuto);
    SmartDashboard.putData("Auto choices", m_chooser);
  }

  /**
   * This function is called every robot packet, no matter the mode. Use this for
   * items like diagnostics that you want ran during disabled, autonomous,
   * teleoperated and test.
   *
   * <p>
   * This runs after the mode specific periodic functions, but before LiveWindow
   * and SmartDashboard integrated updating.
   */
  @Override
  public void robotPeriodic() {
  }

  /**
   * This autonomous (along with the chooser code above) shows how to select
   * between different autonomous modes using the dashboard. The sendable chooser
   * code works with the Java SmartDashboard. If you prefer the LabVIEW Dashboard,
   * remove all of the chooser code and uncomment the getString line to get the
   * auto name from the text box below the Gyro
   *
   * <p>
   * You can add additional auto modes by adding additional comparisons to the
   * switch structure below with additional strings. If using the SendableChooser
   * make sure to add them to the chooser code above as well.
   */
  @Override
  public void autonomousInit() {
    m_autoSelected = m_chooser.getSelected();
  }

  /**
   * This function is called periodically during autonomous.
   */
  @Override
  public void autonomousPeriodic() {
  }

  /**
   * This function is called periodically during operator control.
   */
  @Override
  public void teleopPeriodic() {

    Update_Limelight_Tracking();

    double steer = m_Controller.getX(Hand.kRight);
    double drive = -m_Controller.getY(Hand.kLeft);
    final boolean auto = m_Controller.getAButton();
    final boolean aim = m_Controller.getYButton();
    final boolean swapMode = m_Controller.getXButton();
    final boolean swapBack = m_Controller.getBButton();

    final float Kp = -0.1f;
    final float min_command = 0.05f;

    steer *= 0.70;
    drive *= 0.70;

    if (auto) {
      if (m_LimelightHasValidTarget) {
        m_Drive.arcadeDrive(m_LimelightDriveCommand, m_LimelightSteerCommand);
      } else {
        m_Drive.arcadeDrive(0.0, 0.0);
      }
    } else {
      m_Drive.arcadeDrive(drive, steer);
    }

    if (aim) {
      NetworkTableEntry tx = table.getEntry("tx");
      
      System.out.println("Button A");
      double x = tx.getDouble(0);
      double y = tx.getDouble(0);
      double heading_error = -x;
      double distance_error = -y;
      double steering_adjust = 0.0f;
      double left_command = 0.0f;
      double right_command = 0.0f;
      double min_aim_command = 0.05f;
      double KpDistance = -0.1f;
      double KpAim = -0.1f;
      
      if (x > 1.0) {
        steering_adjust = KpAim*heading_error - min_aim_command;
      } else if (x < 1.0) {
        steering_adjust = KpAim*heading_error + min_aim_command;
      }

      double distance_adjust = KpDistance * distance_error;

      left_command += steering_adjust + distance_adjust;
      right_command -= steering_adjust + distance_adjust;
      m_Drive.tankDrive(left_command, right_command);
    } else {

    }

    if (swapMode) {
      NetworkTableInstance.getDefault().getTable("limelight").getEntry("pipline").setNumber(1);
      /**
       * if (llpipeline < 0) {
       * NetworkTableInstance.getDefault().getTable("limelight").getEntry("pipeline").setNumber(1);
       * } else if (llpipeline > 0 ) {
       * NetworkTableInstance.getDefault().getTable("limelight").getEntry("pipeline").setNumber(0);
       * }
       */
    } else {

    }
    if (swapBack) {
      NetworkTableInstance.getDefault().getTable("limelight").getEntry("pipeline").setNumber(0);
    } else {

    }

  }

  @Override
  public void testPeriodic() {
  }

  /**
   * This function implements a simple method of generating driving and steering
   * commands based on the tracking data from a limelight camera.
   */
  public void Update_Limelight_Tracking() {
    // These numbers must be tuned for your Robot! Be careful!
    final double STEER_K = 0.03; // how hard to turn toward the target
    final double DRIVE_K = 0.26; // how hard to drive fwd toward the target
    final double DESIRED_TARGET_AREA = 13.0; // Area of the target when the robot reaches the wall
    final double MAX_DRIVE = 0.7; // Simple speed limit so we don't drive too fast

    final double tv = NetworkTableInstance.getDefault().getTable("limelight").getEntry("tv").getDouble(0);
    final double tx = NetworkTableInstance.getDefault().getTable("limelight").getEntry("tx").getDouble(0);
    final double ty = NetworkTableInstance.getDefault().getTable("limelight").getEntry("ty").getDouble(0);
    final double ta = NetworkTableInstance.getDefault().getTable("limelight").getEntry("ta").getDouble(0);

    if (tv < 1.0) // if limelight has a valid target, this is 1
    {
      m_LimelightHasValidTarget = false;
      m_LimelightDriveCommand = 0.0;
      m_LimelightSteerCommand = 0.0;
      return;
    }

    m_LimelightHasValidTarget = true;

    // Start with proportional steering
    final double steer_cmd = tx * STEER_K;
        m_LimelightSteerCommand = steer_cmd;

        // try to drive forward until the target area reaches our desired area
        double drive_cmd = (DESIRED_TARGET_AREA - ta) * DRIVE_K;

        // don't let the robot drive too fast into the goal
        if (drive_cmd > MAX_DRIVE)
        {
          drive_cmd = MAX_DRIVE;
        }
        m_LimelightDriveCommand = drive_cmd;
  }
}
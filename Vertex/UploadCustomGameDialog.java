import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.file.Files;

/**
 * UploadCustomGameDialog
 * -----------------------
 * "Upload a project" side of the Roblox-style custom games feature -
 * pick an already-compiled .jar (e.g. exported from BlueJ, the same
 * way every built-in game started out) and publish it to the server's
 * shared catalog. See CustomGameStore's javadoc for the trust model
 * this rests on (no sandboxing - same as CodeEditorWindow's Publish
 * button, which reaches the exact same server request from freshly
 * compiled code instead of a hand-picked file).
 *
 * Themed like the rest of the app's dialogs (HostServerDialog is the
 * closest sibling) - this is the catalog browsing screen, not the
 * code-writing screen, so it isn't the part of this feature the "don't
 * need to match the theme" note was about.
 */
public class UploadCustomGameDialog
{
    private UploadCustomGameDialog()
    {
        // Static utility class - never instantiated.
    }

    /** onUploaded runs (on the EDT) after a successful publish, so the caller can refresh its list. */
    public static void show(final Component anchor, final Runnable onUploaded)
    {
        final JDialog dialog = new JDialog((JFrame) SwingUtilities.getWindowAncestor(anchor), "Upload a Game", true);
        dialog.setUndecorated(true);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.enableTopAccent();
        root.setBorder(new EmptyBorder(24, 24, 20, 24));
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JLabel title = new JLabel("Upload a Game");
        title.setFont(UITheme.FONT_HEADING.deriveFont(18f));
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(title);

        JLabel hint = new JLabel("<html><body style='width:280px'>Pick a compiled .jar whose entry class is a public class extending JFrame with a public no-argument constructor - exactly how every built-in game already works. It becomes playable by everyone on this server.</body></html>");
        hint.setFont(UITheme.FONT_SMALL);
        hint.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setBorder(new EmptyBorder(6, 0, 18, 0));
        root.add(hint);

        JLabel nameLabel = new JLabel("Game name");
        nameLabel.setFont(UITheme.FONT_SMALL);
        nameLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameLabel.setBorder(new EmptyBorder(0, 0, 6, 0));
        root.add(nameLabel);

        final ThemedTextField nameField = new ThemedTextField("e.g. Asteroid Dash");
        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameField.setMaximumSize(new Dimension(2000, 38));
        root.add(nameField);
        root.add(javax.swing.Box.createVerticalStrut(16));

        JLabel classLabel = new JLabel("Entry class name");
        classLabel.setFont(UITheme.FONT_SMALL);
        classLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        classLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        classLabel.setBorder(new EmptyBorder(0, 0, 6, 0));
        root.add(classLabel);

        final ThemedTextField classField = new ThemedTextField("e.g. AsteroidDashWindow");
        classField.setAlignmentX(Component.LEFT_ALIGNMENT);
        classField.setMaximumSize(new Dimension(2000, 38));
        root.add(classField);
        root.add(javax.swing.Box.createVerticalStrut(16));

        final JLabel fileLabel = new JLabel("No file chosen");
        fileLabel.setFont(UITheme.FONT_SMALL);
        fileLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        fileLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        fileLabel.setBorder(new EmptyBorder(0, 0, 10, 0));

        final File[] chosenFile = new File[1];

        ThemedButton chooseButton = new ThemedButton("Choose .jar File...", false);
        chooseButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        chooseButton.setMaximumSize(new Dimension(220, 34));
        chooseButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileFilter(new FileNameExtensionFilter("Game jar (*.jar)", "jar"));
                int result = chooser.showOpenDialog(dialog);
                if (result == JFileChooser.APPROVE_OPTION)
                {
                    chosenFile[0] = chooser.getSelectedFile();
                    fileLabel.setText(chosenFile[0].getName());
                }
            }
        });
        root.add(chooseButton);
        root.add(javax.swing.Box.createVerticalStrut(10));
        root.add(fileLabel);

        final JLabel errorLabel = new JLabel(" ");
        errorLabel.setFont(UITheme.FONT_SMALL);
        errorLabel.setForeground(new Color(230, 90, 90));
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        errorLabel.setBorder(new EmptyBorder(0, 0, 8, 0));
        root.add(errorLabel);

        final ThemedButton uploadButton = new ThemedButton("Upload", true);
        uploadButton.setPreferredSize(new Dimension(110, 40));

        uploadButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                errorLabel.setText(" ");

                final String name = nameField.getValue().trim();
                final String entryClass = classField.getValue().trim();
                final File file = chosenFile[0];

                if (name.isEmpty())
                {
                    errorLabel.setText("Give your game a name first.");
                    return;
                }
                if (entryClass.isEmpty())
                {
                    errorLabel.setText("Enter the entry class name (the JFrame subclass to launch).");
                    return;
                }
                if (file == null)
                {
                    errorLabel.setText("Choose a .jar file to upload.");
                    return;
                }

                uploadButton.setEnabled(false);
                uploadButton.setText("Uploading...");

                Thread worker = new Thread(new Runnable()
                {
                    public void run()
                    {
                        final String errorMessage = doUpload(name, entryClass, file);

                        SwingUtilities.invokeLater(new Runnable()
                        {
                            public void run()
                            {
                                if (errorMessage != null)
                                {
                                    uploadButton.setEnabled(true);
                                    uploadButton.setText("Upload");
                                    errorLabel.setText(errorMessage);
                                    return;
                                }
                                dialog.dispose();
                                if (onUploaded != null)
                                {
                                    onUploaded.run();
                                }
                            }
                        });
                    }
                });
                worker.start();
            }
        });

        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(uploadButton);
        root.add(row);

        dialog.pack();
        dialog.setLocationRelativeTo(anchor);
        dialog.setVisible(true);
    }

    /** Runs on a background thread. Returns null on success, or a user-facing error message. */
    private static String doUpload(String name, String entryClass, File file)
    {
        byte[] jarBytes;
        try
        {
            jarBytes = Files.readAllBytes(file.toPath());
        }
        catch (Exception e)
        {
            return "Could not read that file: " + e.getMessage();
        }

        return CustomGameUploader.upload(name, entryClass, jarBytes);
    }
}

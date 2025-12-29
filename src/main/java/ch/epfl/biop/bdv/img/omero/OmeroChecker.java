/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2025 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package ch.epfl.biop.bdv.img.omero;

import org.scijava.Context;
import org.scijava.ui.UIService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;

public class OmeroChecker {

    private static boolean alreadyChecked = false;

    public synchronized static void PromptUserIfOmeroDependenciesMissing() {
        PromptUserIfOmeroDependenciesMissing(null);
    }

    public synchronized static void PromptUserIfOmeroDependenciesMissing(Context ctx) {
        // We don't want to display this message too many times.
        if (alreadyChecked) return;
        alreadyChecked = true;
        try {
            Class.forName("net.imagej.omero.OMEROService");
        } catch (ClassNotFoundException e) {
            if ((ctx!=null)&&(ctx.getService(UIService.class) != null) && (!ctx.getService(UIService.class).isHeadless())) {
                // Graphical user interface available
                showOmeroMissingDialog();
            } else {
                System.err.println("You are trying to open an OMERO dataset but its dependencies are missing. Please install the OMERO 5.5-5.6 update site into Fiji.");
            }
        }
    }

    public static void showOmeroMissingDialog() {
        SwingUtilities.invokeLater(() -> {
            JDialog dialog = new JDialog((Frame) null, "OMERO Dependencies Missing", true);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setLayout(new BorderLayout(10, 10));

            // Main panel
            JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
            mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

            // Icon and title panel
            JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            JLabel iconLabel = new JLabel(UIManager.getIcon("OptionPane.warningIcon"));
            JLabel titleLabel = new JLabel("OMERO Dependencies Not Found");
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
            headerPanel.add(iconLabel);
            headerPanel.add(titleLabel);

            // Message panel
            JPanel messagePanel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(5, 0, 5, 0);

            JLabel line1 = new JLabel("The OMERO dependencies are not installed in your Fiji installation.");
            JLabel line2 = new JLabel("To use OMERO features, please install the OMERO update site:");
            line2.setFont(line2.getFont().deriveFont(Font.PLAIN));

            messagePanel.add(line1, gbc);
            gbc.gridy++;
            messagePanel.add(line2, gbc);

            // Instructions panel with steps
            JPanel instructionsPanel = new JPanel();
            instructionsPanel.setLayout(new BoxLayout(instructionsPanel, BoxLayout.Y_AXIS));
            instructionsPanel.setBorder(BorderFactory.createCompoundBorder(
                    new EmptyBorder(10, 20, 10, 20),
                    BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1)
            ));
            instructionsPanel.setBackground(new Color(245, 245, 245));

            String[] steps = {
                    "1. Go to Help ▸ Update...",
                    "2. Click 'Manage update sites'",
                    "3. Check the box for 'OMERO-5.5-5.6'",
                    "4. Click 'Close' and then 'Apply changes'",
                    "5. Restart Fiji"
            };

            for (String step : steps) {
                JLabel stepLabel = new JLabel(step);
                stepLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
                stepLabel.setBorder(new EmptyBorder(3, 5, 3, 5));
                instructionsPanel.add(stepLabel);
            }

            // Combine message and instructions
            JPanel contentPanel = new JPanel(new BorderLayout(0, 10));
            contentPanel.add(messagePanel, BorderLayout.NORTH);
            contentPanel.add(instructionsPanel, BorderLayout.CENTER);

            // Button panel
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

            JButton docsButton = new JButton("Open Documentation");
            docsButton.addActionListener(e -> {
                try {
                    Desktop.getDesktop().browse(new URI("https://imagej.net/software/omero"));
                } catch (Exception ex) {
                    // Silently fails if it can't open a browser
                }
            });

            JButton closeButton = new JButton("OK");
            closeButton.addActionListener(e -> dialog.dispose());

            buttonPanel.add(docsButton);
            buttonPanel.add(closeButton);

            // Assemble dialog
            mainPanel.add(headerPanel, BorderLayout.NORTH);
            mainPanel.add(contentPanel, BorderLayout.CENTER);
            mainPanel.add(buttonPanel, BorderLayout.SOUTH);

            dialog.add(mainPanel);
            dialog.pack();
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
        });
    }
}

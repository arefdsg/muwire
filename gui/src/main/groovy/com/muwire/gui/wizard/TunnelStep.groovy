package com.muwire.gui.wizard

import java.awt.BorderLayout
import java.awt.GridBagConstraints
import static com.muwire.gui.Translator.trans

import javax.swing.JLabel
import javax.swing.border.TitledBorder

import com.muwire.core.MuWireSettings

class TunnelStep extends WizardStep {

    
    def tunnelLengthSlider
    def tunnelQuantitySlider
    
    public TunnelStep(WizardDefaults defaults) {
        super("tunnels", defaults)
    }

    @Override
    protected void buildUI(FactoryBuilderSupport builder, def nextAction) {
        builder.panel (constraints : getConstraint()) {
            gridBagLayout()
            panel (border : titledBorder(title : trans("SPEED_VS_ANONYMITY"), border : etchedBorder(), titlePosition: TitledBorder.TOP,
                constraints : gbc(gridx: 0, gridy: 0, fill : GridBagConstraints.HORIZONTAL, weightx : 100))) {
                borderLayout()
                def lengthTable = new Hashtable()
                lengthTable.put(1, new JLabel(trans("MAX_SPEED")))
                lengthTable.put(3, new JLabel(trans("MAX_ANONYMITY")))
                tunnelLengthSlider = slider(minimum : 1, maximum : 3, value : defaults.tunnelLength,
                    majorTickSpacing : 1, snapToTicks: true, paintTicks: true, labelTable : lengthTable,
                    paintLabels : true, constraints : BorderLayout.CENTER)
            }
            panel (border : titledBorder(title : trans("RELIABILITY_VS_RESOURCES"), border : etchedBorder(), titlePosition: TitledBorder.TOP,
                constraints : gbc(gridx: 0, gridy: 1, fill : GridBagConstraints.HORIZONTAL, weightx : 100))) {
                borderLayout()
                def quantityTable = new Hashtable()
                quantityTable.put(1, new JLabel(trans("MIN_RESOURCES")))
                quantityTable.put(6, new JLabel(trans("MAX_RELIABILITY")))
                tunnelQuantitySlider = slider(minimum : 1, maximum : 6, value : defaults.tunnelQuantity,
                    majorTickSpacing : 1, snapToTicks : true, paintTicks: true, labelTable : quantityTable,
                    paintLabels : true, constraints : BorderLayout.CENTER)
            }
            panel(constraints : gbc(gridx:0, gridy: 2, weighty: 100))
        }
    }

    @Override
    protected List<String> validate() {
        return null
    }

    @Override
    protected void apply(MuWireSettings muSettings, Properties i2pSettings) {
        String tunnelQuantity = tunnelQuantitySlider.value
        i2pSettings['inbound.quantity'] = tunnelQuantity
        i2pSettings['outbound.quantity'] = tunnelQuantity
        
        String tunnelLength = String.valueOf(tunnelLengthSlider.value)
        i2pSettings['inbound.length'] = tunnelLength
        i2pSettings['outbound.length'] = tunnelLength
        
        String excludePeerCaps
        switch (tunnelLength) {
            case "1" : excludePeerCaps = "KLMNO"; break;
            case "2" : excludePeerCaps = "KLMN"; break;
            case "3" : excludePeerCaps = "KLM"; break
            default :
                excludePeerCaps = "" // assume user knows what they're doing
        }
        i2pSettings['router.excludePeerCaps'] = excludePeerCaps
    }
}

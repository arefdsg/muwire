package com.muwire.clilanterna

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.Button
import com.googlecode.lanterna.gui2.GridLayout
import com.googlecode.lanterna.gui2.GridLayout.Alignment
import com.googlecode.lanterna.gui2.LayoutData
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.TextBox
import com.googlecode.lanterna.gui2.Window

class UpdateTextView extends BasicWindow {
    private final TextBox textBox
    private final LayoutData layoutData = GridLayout.createLayoutData(Alignment.CENTER, Alignment.CENTER)
    
    UpdateTextView(String text, TerminalSize terminalSize) {
        super("Update Details")
        
        setHints([Window.Hint.CENTERED])
        
        Panel contentPanel = new Panel()
        contentPanel.setLayoutManager(new GridLayout(1))
        
        TerminalSize boxSize = new TerminalSize((terminalSize.getColumns() / 2).toInteger(), (terminalSize.getRows() / 2).toInteger())
        textBox = new TextBox(boxSize, text, TextBox.Style.MULTI_LINE)
        contentPanel.addComponent(textBox, layoutData)
        
        Button closeButton = new Button("Close", {close()})
        contentPanel.addComponent(closeButton, layoutData)
        
        setComponent(contentPanel)
    }
}

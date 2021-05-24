package com.muwire.gui

import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

import com.muwire.core.download.Downloader

import net.i2p.data.DataHelper

class DownloadProgressRenderer extends DefaultTableCellRenderer {
    DownloadProgressRenderer() {
        setHorizontalAlignment(JLabel.CENTER)
    }
    
    @Override
    JComponent getTableCellRendererComponent(JTable table, Object value,
        boolean isSelected, boolean hasFocus, int row, int column) {
        Downloader d = (Downloader) value
        int pieces = d.nPieces
        int done = d.donePieces()
        int percent = -1
        if (pieces != 0)
            percent = (done * 100 / pieces)
        StringBuffer sb = new StringBuffer()
        SizeFormatter.format(d.length, sb)
        String totalSize = sb.toString() + "B"
        setText(String.format("%2d", percent) + "% of ${totalSize}".toString())
        
        if (isSelected) {
            setForeground(table.getSelectionForeground())
            setBackground(table.getSelectionBackground())
        } else {
            setForeground(table.getForeground())
            setBackground(table.getBackground())
        }
        this
    }
    
}

package com.muwire.gui

import griffon.core.artifact.GriffonView
import static com.muwire.gui.Translator.trans
import griffon.inject.MVCMember
import griffon.metadata.ArtifactProviderFor
import net.i2p.data.Base64

import javax.swing.JDialog
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

import com.muwire.core.Persona
import com.muwire.core.filecert.Certificate

import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

import javax.annotation.Nonnull

@ArtifactProviderFor(GriffonView)
class CertificateControlView {
    @MVCMember @Nonnull
    FactoryBuilderSupport builder
    @MVCMember @Nonnull
    CertificateControlModel model
    @MVCMember @Nonnull
    CertificateControlController controller

    def mainFrame
    def dialog
    def panel
    def usersTable
    def certsTable
    def lastUsersSortEvent
    def lastCertsSortEvent
    
    void initUI() {
        mainFrame = application.windowManager.findWindow("main-frame")
        int rowHeight = application.context.get("row-height")
        dialog = new JDialog(mainFrame,trans("CERTIFICATES"),true)
        dialog.setResizable(true)
        
        panel = builder.panel {
            borderLayout()
            panel(constraints : BorderLayout.NORTH) {
                label(trans("CERTIFICATES_IN_REPOSITORY"))
            }
            panel (constraints : BorderLayout.CENTER) {
                gridLayout(rows : 1, cols : 2)
                scrollPane {
                    usersTable = table(autoCreateRowSorter : true, rowHeight : rowHeight) {
                        tableModel(list : model.users) {
                            closureColumn(header : trans("ISSUER"), type : String, read : {it.getHumanReadableName()})
                        }
                    }
                }
                scrollPane {
                    certsTable = table(autoCreateRowSorter : true, rowHeight : rowHeight) {
                        tableModel(list : model.certificates) {
                            closureColumn(header : trans("FILE_NAME"), type : String, read : {HTMLSanitizer.sanitize(it.name.name)})
                            closureColumn(header : trans("HASH"), type : String, read : {Base64.encode(it.infoHash.getRoot())})
                            closureColumn(header : trans("COMMENT"), preferredWidth : 20, type : Boolean, read : {it.comment != null})
                            closureColumn(header : trans("TIMESTAMP"), type : Long, read : { it.timestamp })
                        }
                    }
                }
            }
            panel (constraints : BorderLayout.SOUTH) {
                button(text : trans("VIEW_COMMENT"), enabled : bind {model.showCommentActionEnabled}, showCommentAction)
                button(text : trans("CLOSE"), closeAction)
            }
        }
    }
    
    void mvcGroupInit(Map<String,String> args) {
        usersTable.rowSorter.addRowSorterListener({evt -> lastUsersSortEvent = evt})
        def selectionModel = usersTable.getSelectionModel()
        selectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        selectionModel.addListSelectionListener({
            Persona issuer = getSelectedIssuer()
            if (issuer == null)
                return
            Set<Certificate> certs = model.core.certificateManager.byIssuer.get(issuer)
            if (certs == null)
                return
            model.certificates.clear()
            model.certificates.addAll(certs)
            certsTable.model.fireTableDataChanged()
        })
        
        certsTable.rowSorter.addRowSorterListener({evt -> lastCertsSortEvent = evt})
        selectionModel = certsTable.getSelectionModel()
        selectionModel.addListSelectionListener({
            Certificate c = getSelectedSertificate()
            model.showCommentActionEnabled = c != null && c.comment != null 
        })
        
        certsTable.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger())
                    showMenu(e)
            }
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger())
                    showMenu(e)
            }
        })
        
        certsTable.setDefaultRenderer(Long.class, new DateRenderer())
        
        dialog.getContentPane().add(panel)
        dialog.pack()
        dialog.setLocationRelativeTo(mainFrame)
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE)
        dialog.addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent e) {
                mvcGroup.destroy()
            }
        })
        dialog.show()
    }
    
    private Persona getSelectedIssuer() {
        int selectedRow = usersTable.getSelectedRow()
        if (selectedRow < 0)
            return null
        if (lastUsersSortEvent != null)
            selectedRow = usersTable.rowSorter.convertRowIndexToModel(selectedRow)
        model.users[selectedRow]
    }
    
    Certificate getSelectedSertificate() {
        int [] selectedRows = certsTable.getSelectedRows()
        if (selectedRows.length != 1) 
            return null
        if (lastCertsSortEvent != null)
            selectedRows[0] = certsTable.rowSorter.convertRowIndexToModel(selectedRows[0])
        model.certificates[selectedRows[0]]
    }
    
    private void showMenu(MouseEvent e) {
        if (!model.showCommentActionEnabled)
            return
        JPopupMenu menu = new JPopupMenu()
        JMenuItem showComment = new JMenuItem(trans("VIEW_COMMENT"))
        showComment.addActionListener({controller.showComment()})
        menu.add(showComment)
        menu.show(e.getComponent(), e.getX(), e.getY())
    }
    
}
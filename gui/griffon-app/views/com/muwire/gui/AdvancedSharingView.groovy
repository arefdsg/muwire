package com.muwire.gui

import griffon.core.artifact.GriffonView
import static com.muwire.gui.Translator.trans
import griffon.inject.MVCMember
import griffon.metadata.ArtifactProviderFor
import net.i2p.data.DataHelper

import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JTabbedPane
import javax.swing.JTree
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer

import com.muwire.core.files.directories.WatchedDirectory

import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

import javax.annotation.Nonnull

@ArtifactProviderFor(GriffonView)
class AdvancedSharingView {
    @MVCMember @Nonnull
    FactoryBuilderSupport builder
    @MVCMember @Nonnull
    AdvancedSharingModel model
    @MVCMember @Nonnull
    AdvancedSharingController controller

    def mainFrame
    def dialog
    def watchedDirsPanel
    def negativeTreePanel
    
    def watchedDirsTable
    def watchedDirsTableSortEvent
    
    void initUI() {
        mainFrame = application.windowManager.findWindow("main-frame")
        int rowHeight = application.context.get("row-height")
        dialog = new JDialog(mainFrame,trans("ADVANCED_SHARING"),true)
        dialog.setResizable(true)
        
        watchedDirsPanel = builder.panel {
            borderLayout()
            panel (constraints : BorderLayout.NORTH) {
                label(text : trans("DIRECTORIES_WATCHED_FOR_CHANGES"))
            }
            scrollPane( constraints : BorderLayout.CENTER ) {
                watchedDirsTable = table(autoCreateRowSorter : true, rowHeight : rowHeight) {
                    tableModel(list : model.watchedDirectories) {
                        closureColumn(header : trans("DIRECTORY"), preferredWidth: 350, type : String, read : {it.directory.toString()})
                        closureColumn(header : trans("AUTO"), preferredWidth: 100, type : Boolean, read : {it.autoWatch})
                        closureColumn(header : trans("INTERVAL"), preferredWidth : 100, type : Integer, read : {it.syncInterval})
                        closureColumn(header : trans("LAST_SYNC"), preferredWidth: 250, type : Long, read : {it.lastSync})
                    }
                }
            }
            panel (constraints : BorderLayout.SOUTH) {
                button(text : trans("CONFIGURE"), enabled : bind{model.configureActionEnabled}, configureAction)
                button(text : trans("SYNC"), enabled : bind{model.syncActionEnabled}, syncAction)
            }
        }
        
        negativeTreePanel = builder.panel {
            borderLayout()
            panel(constraints : BorderLayout.NORTH) {
                label(text : trans("FILES_NOT_SHARED"))
            }
            scrollPane( constraints : BorderLayout.CENTER ) {
                def jtree = new JTree(model.negativeTree)
                tree(rootVisible : false, rowHeight : rowHeight,jtree)
            }
        }
        
        def centerRenderer = new DefaultTableCellRenderer()
        centerRenderer.setHorizontalAlignment(JLabel.CENTER)
        watchedDirsTable.setDefaultRenderer(Long.class, new DateRenderer())
        watchedDirsTable.setDefaultRenderer(Integer.class, centerRenderer)
        
        watchedDirsTable.rowSorter.addRowSorterListener({evt -> watchedDirsTableSortEvent = evt})
        def selectionModel = watchedDirsTable.getSelectionModel()
        selectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        selectionModel.addListSelectionListener({
            def directory = selectedWatchedDirectory()
            model.syncActionEnabled = !(directory == null || directory.autoWatch)
            model.configureActionEnabled = directory != null
        })
        
        watchedDirsTable.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger())
                    showMenu(e)
            }
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger())
                    showMenu(e)
            }
        })
    }
    
    private void showMenu(MouseEvent e) {
        JPopupMenu menu = new JPopupMenu()
        JMenuItem configure = new JMenuItem(trans("CONFIGURE"))
        configure.addActionListener({controller.configure()})
        menu.add(configure)
        
        if (model.syncActionEnabled) {
            JMenuItem sync = new JMenuItem(trans("SYNC"))
            sync.addActionListener({controller.sync()})
            menu.add(sync)
        }
        
        menu.show(e.getComponent(), e.getX(), e.getY())
    }
    
    WatchedDirectory selectedWatchedDirectory() {
        int row = watchedDirsTable.getSelectedRow()
        if (row < 0)
            return null
        if (watchedDirsTableSortEvent != null)
            row = watchedDirsTable.rowSorter.convertRowIndexToModel(row)
        model.watchedDirectories[row]
    }
    
    void mvcGroupInit(Map<String,String> args) {
        def tabbedPane = new JTabbedPane()
        tabbedPane.addTab(trans("WATCHED_DIRECTORIES"), watchedDirsPanel)
        tabbedPane.addTab(trans("NEGATIVE_TREE"), negativeTreePanel)
        
        dialog.with {
            getContentPane().add(tabbedPane)
            pack()
            setLocationRelativeTo(mainFrame)
            setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE)
            addWindowListener(new WindowAdapter() {
                public void windowClosed(WindowEvent e) {
                    mvcGroup.destroy()
                }
            })
            show()
        }
    }
}
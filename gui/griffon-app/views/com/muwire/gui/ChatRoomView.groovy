package com.muwire.gui

import com.muwire.gui.profile.PersonaOrProfile
import com.muwire.gui.profile.PersonaOrProfileCellRenderer
import com.muwire.gui.profile.PersonaOrProfileComparator
import griffon.core.artifact.GriffonView
import static com.muwire.gui.Translator.trans
import griffon.inject.MVCMember
import griffon.metadata.ArtifactProviderFor
import net.i2p.data.DataHelper

import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JSplitPane
import javax.swing.JTextPane
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.text.Element
import javax.swing.text.Style
import javax.swing.text.StyleConstants
import javax.swing.text.StyleContext
import javax.swing.text.StyledDocument
import javax.swing.SpringLayout.Constraints

import com.muwire.core.Persona
import com.muwire.core.chat.ChatConnectionAttemptStatus

import java.awt.BorderLayout
import java.awt.Color
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

import javax.annotation.Nonnull

@ArtifactProviderFor(GriffonView)
class ChatRoomView {
    @MVCMember @Nonnull
    FactoryBuilderSupport builder
    @MVCMember @Nonnull
    ChatRoomModel model
    @MVCMember @Nonnull
    ChatRoomController controller

    ChatNotificator chatNotificator
    
    def pane
    def parent
    def sayField
    JTextPane roomTextArea
    def textScrollPane
    def membersTable
    def lastMembersTableSortEvent
    
    void initUI() {
        int rowHeight = application.context.get("row-height")
        def parentModel = mvcGroup.parentGroup.model
        if (model.console || model.privateChat) {
            pane = builder.panel {
                borderLayout()
                panel(constraints : BorderLayout.CENTER) {
                    gridLayout(rows : 1, cols : 1)
                    textScrollPane = scrollPane {
                        roomTextArea = textPane(editable : false)
                    }
                }
                panel(constraints : BorderLayout.SOUTH) {
                    borderLayout()
                    label(text : trans("SAY_SOMETHING_HERE") + ": ", constraints : BorderLayout.WEST)
                    sayField = textField(enabled : bind {parentModel.sayActionEnabled}, actionPerformed : {controller.say()}, constraints : BorderLayout.CENTER)
                }
            }
        } else {
            pane = builder.panel {
                borderLayout()
                panel(constraints : BorderLayout.CENTER) {
                    gridLayout(rows : 1, cols : 1)
                    splitPane(orientation : JSplitPane.HORIZONTAL_SPLIT, continuousLayout : true, dividerLocation : 300) {
                        panel {
                            gridLayout(rows : 1, cols : 1)
                            scrollPane {
                                membersTable = table(autoCreateRowSorter : true, rowHeight : rowHeight) {
                                    tableModel(list : model.members) {
                                        closureColumn(header : trans("NAME"), preferredWidth: 100, type: PersonaOrProfile, read : {it})
                                        closureColumn(header : trans("TRUST_STATUS"), preferredWidth: 30, type : String, 
                                                read : {trans(model.core.trustService.getLevel(it.getPersona().destination).name())})
                                    }
                                }
                            }
                        }
                        panel {
                            gridLayout(rows : 1, cols : 1)
                            textScrollPane = scrollPane {
                                roomTextArea = textPane(editable : false)
                            }
                        }
                    }
                }
                panel(constraints : BorderLayout.SOUTH) {
                    borderLayout()
                    label(text : trans("SAY_SOMETHING_HERE") + ": ", constraints : BorderLayout.WEST)
                    sayField = textField(enabled : bind {parentModel.sayActionEnabled}, actionPerformed : {controller.say()}, constraints : BorderLayout.CENTER)
                }

            }
        }
        
        SmartScroller smartScroller = new SmartScroller(textScrollPane)
        pane.putClientProperty("mvcId", mvcGroup.mvcId)
    }
    
    void mvcGroupInit(Map<String,String> args) {
        parent = mvcGroup.parentGroup.view.builder.getVariable(model.tabName)
        parent.addTab(model.roomTabName, pane)
        
        int index = parent.indexOfComponent(pane)
        parent.setSelectedIndex(index)
        
        def tabPanel = builder.panel {
            borderLayout()
            panel (constraints : BorderLayout.CENTER) {
                label(text : model.roomTabName)
            }
            button(icon : imageIcon("/close_tab.png"), preferredSize: [20, 20], constraints : BorderLayout.EAST,
                actionPerformed : closeTab )
        }
        if (!model.console)
            parent.setTabComponentAt(index, tabPanel)
            
        if (membersTable != null) {
            
            membersTable.setDefaultRenderer(PersonaOrProfile.class, new PersonaOrProfileCellRenderer())
            membersTable.rowSorter.setComparator(0, new PersonaOrProfileComparator())
            membersTable.rowSorter.addRowSorterListener({evt -> lastMembersTableSortEvent = evt})
            membersTable.rowSorter.setSortsOnUpdates(true)
            membersTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            
            membersTable.addMouseListener(new MouseAdapter() {
                        public void mouseClicked(MouseEvent e) {
                            if (e.button == MouseEvent.BUTTON1 && e.clickCount > 1) {
                                controller.privateMessage()
                            } else if (e.isPopupTrigger() || e.button == MouseEvent.BUTTON3)
                                showPopupMenu(e)
                        }
                        
                        public void mouseReleased(MouseEvent e) {
                            if (e.isPopupTrigger() || e.button == MouseEvent.BUTTON3)
                                showPopupMenu(e)
                        }
                    })
        }
        
        // styles
        StyledDocument document = roomTextArea.getStyledDocument()
        Style regular = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE)
        Style italic = document.addStyle("italic", regular)
        StyleConstants.setItalic(italic, true)
        Style gray = document.addStyle("gray", regular)
        StyleConstants.setForeground(gray, Color.GRAY)
          
    }
    
    private void showPopupMenu(MouseEvent e) {
        JPopupMenu menu = new JPopupMenu()
        JMenuItem privateChat = new JMenuItem(trans("START_PRIVATE_CHAT"))
        privateChat.addActionListener({controller.privateMessage()})
        menu.add(privateChat)
        JMenuItem browse = new JMenuItem(trans("BROWSE"))
        browse.addActionListener({controller.browse()})
        menu.add(browse)
        JMenuItem viewProfile = new JMenuItem(trans("VIEW_PROFILE"))
        viewProfile.addActionListener({controller.viewProfile()})
        menu.add(viewProfile)
        JMenuItem markTrusted = new JMenuItem(trans("MARK_TRUSTED"))
        markTrusted.addActionListener({controller.markTrusted()})
        menu.add(markTrusted)
        JMenuItem markNeutral = new JMenuItem(trans("MARK_NEUTRAL"))
        markNeutral.addActionListener({controller.markNeutral()})
        menu.add(markNeutral)
        JMenuItem markDistrusted = new JMenuItem(trans("MARK_DISTRUSTED"))
        markDistrusted.addActionListener({controller.markDistrusted()})
        menu.add(markDistrusted)
        menu.show(e.getComponent(), e.getX(), e.getY())
    }
    
    Persona getSelectedPersona() {
        getSelectedPOP()?.getPersona()
    }
    
    PersonaOrProfile getSelectedPOP() {
        int selectedRow = membersTable.getSelectedRow()
        if (selectedRow < 0)
            return null
        if (lastMembersTableSortEvent != null)
            selectedRow = membersTable.rowSorter.convertRowIndexToModel(selectedRow)
        model.members[selectedRow]
    }
    
    void refreshMembersTable() {
        int selectedRow = membersTable.getSelectedRow()
        membersTable.model.fireTableDataChanged()
        membersTable.selectionModel.setSelectionInterval(selectedRow, selectedRow)
    }
    
    def closeTab = {
        int index = parent.indexOfComponent(pane)
        parent.removeTabAt(index)
        controller.leaveRoom()
        chatNotificator.roomClosed(mvcGroup.mvcId)
        mvcGroup.destroy()
    }
    
    void appendGray(String gray) {
        StyledDocument doc = roomTextArea.getStyledDocument()
        doc.insertString(doc.getEndPosition().getOffset() - 1, gray, doc.getStyle("gray"))
    }
    
    void appendSay(String text, Persona sender, long timestamp) {
        StyledDocument doc = roomTextArea.getStyledDocument()
        String header = DataHelper.formatTime(timestamp) + " <" + sender.getHumanReadableName() + "> "
        doc.insertString(doc.getEndPosition().getOffset() - 1, header, doc.getStyle("italic"))
        doc.insertString(doc.getEndPosition().getOffset() - 1, text, doc.getStyle("regular"))
        doc.insertString(doc.getEndPosition().getOffset() - 1, "\n", doc.getStyle("regular"))
    }
    
    int getLineCount() {
        StyledDocument doc = roomTextArea.getStyledDocument()
        doc.getDefaultRootElement().getElementCount() - 1
    }
    
    void removeFirstLine() {
        StyledDocument doc = roomTextArea.getStyledDocument()
        Element element = doc.getParagraphElement(0)
        doc.remove(0, element.getEndOffset())
    }
}
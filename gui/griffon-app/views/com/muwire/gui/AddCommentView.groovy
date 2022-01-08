package com.muwire.gui

import griffon.core.artifact.GriffonView
import static com.muwire.gui.Translator.trans
import griffon.inject.MVCMember
import griffon.metadata.ArtifactProviderFor
import net.i2p.data.Base64

import javax.swing.JDialog
import javax.swing.SwingConstants

import com.muwire.core.util.DataUtil

import java.awt.BorderLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

import javax.annotation.Nonnull

@ArtifactProviderFor(GriffonView)
class AddCommentView {
    @MVCMember @Nonnull
    FactoryBuilderSupport builder
    @MVCMember @Nonnull
    AddCommentModel model

    def mainFrame
    def dialog
    def p
    def textarea
    
    void initUI() {
        mainFrame = application.windowManager.findWindow("main-frame")
        String title = trans("ADD_COMMENT_MULTIPLE")
        String comment = ""
        if (model.selectedFiles.size() == 1) {
            title = trans("ADD_COMMENT_SINGLE",model.selectedFiles[0].getFile().getName())
            if (model.selectedFiles[0].comment != null)
                comment = DataUtil.readi18nString(Base64.decode(model.selectedFiles[0].comment))
        }
        dialog = new JDialog(mainFrame, title, true)
        
        p = builder.panel {
            borderLayout()
            panel (constraints : BorderLayout.CENTER) {
                scrollPane {
                    textarea = textArea(text : comment, rows : 20, columns : 100, editable : true, lineWrap:true, wrapStyleWord:true)
                }
            }
            panel (constraints : BorderLayout.SOUTH) {
                button(text : trans("SAVE"), saveAction)
                button(text : trans("CANCEL"), cancelAction)
            }
        }
    }
    
    void mvcGroupInit(Map<String,String> args) {
        dialog.getContentPane().add(p)
        dialog.pack()
        dialog.setLocationRelativeTo(mainFrame)
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE)
        dialog.show()
    }
}
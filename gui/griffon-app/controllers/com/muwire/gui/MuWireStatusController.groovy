package com.muwire.gui

import griffon.core.artifact.GriffonController
import griffon.core.controller.ControllerAction
import griffon.inject.MVCMember
import griffon.metadata.ArtifactProviderFor
import javax.annotation.Nonnull

import com.muwire.core.Core

@ArtifactProviderFor(GriffonController)
class MuWireStatusController {
    @MVCMember @Nonnull
    MuWireStatusModel model
    @MVCMember @Nonnull
    MuWireStatusView view

    @ControllerAction
    void refresh() {
        Core core = application.context.get("core")

        int incoming = 0
        int outgoing = 0
        core.connectionManager.getConnections().each {
            if (it.incoming)
                incoming++
            else
                outgoing++
        }
        model.incomingConnections = incoming
        model.outgoingConnections = outgoing

        model.knownHosts = core.hostCache.countAllHosts()
        model.failingHosts = core.hostCache.countFailingHosts()
        model.hopelessHosts = core.hostCache.countHopelessHosts()
        

        model.sharedFiles = core.fileManager.fileToSharedFile.size()
        model.downloads = core.downloadManager.downloaders.size()
        model.browsed = core.connectionAcceptor.browsed
    }

    @ControllerAction
    void close() {
        view.dialog.setVisible(false)
    }
}
package org.vaadin.jonatan.contexthelp.widgetset.client.ui;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.*;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.shared.*;
import com.google.gwt.user.client.*;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Event.NativePreviewHandler;
import com.google.gwt.user.client.ui.HTML;
import com.vaadin.client.ApplicationConnection;
import com.vaadin.client.ui.VOverlay;
import org.vaadin.jonatan.contexthelp.widgetset.client.ui.ContextHelpEvent.*;

public class VContextHelp implements NativePreviewHandler, HasHandlers {

    private static final int SCROLL_UPDATER_INTERVAL = 100;

    /**
     * Set the CSS class name to allow styling.
     */
    public static final String CLASSNAME = "v-contexthelp";

    private boolean followFocus = false;

    private boolean hidden = true;
    private boolean closeButton = false;

    private final HelpBubble bubble;

    private final Timer scrollUpdater;

    private int helpKeyCode = 112; // F1 by default

    private boolean hideOnBlur = true;

    private HandlerManager handlerManager;
    private ApplicationConnection connection;


    /**
     * The constructor should first call super() to initialize the component and
     * then handle any initialization relevant to Vaadin.
     */
    public VContextHelp(ApplicationConnection connection) {
        super();
        this.connection = connection;

        handlerManager = new HandlerManager(this);

        Event.addNativePreviewHandler(this);
        suppressHelpForIE();

        bubble = new HelpBubble();
        scrollUpdater = new Timer() {
            public void run() {
                bubble.updatePositionIfNeeded();
            }
        };
    }

    public HandlerRegistration addBubbleShownHandler(BubbleShownHandler handler) {
        return handlerManager.addHandler(BubbleShownEvent.TYPE, handler);
    }

    public HandlerRegistration addBubbleHiddenHandler(BubbleHiddenHandler handler) {
        return handlerManager.addHandler(BubbleHiddenEvent.TYPE, handler);
    }

    public HandlerRegistration addBubbleMovedHandler(BubbleMovedHandler handler) {
        return handlerManager.addHandler(BubbleMovedEvent.TYPE, handler);
    }

    private void fireBubbleShownEvent(String componentId, String helpHtml) {
        handlerManager.fireEvent(new BubbleShownEvent(componentId, helpHtml));
    }

    private void fireBubbleHiddenEvent() {
        handlerManager.fireEvent(new BubbleHiddenEvent());
    }

    private void fireBubbleMovedEvent(String componentId) {
        handlerManager.fireEvent(new BubbleMovedEvent(componentId));
    }

    @Override
    public void fireEvent(GwtEvent<?> event) {
        handlerManager.fireEvent(event);
    }

    public void onPreviewNativeEvent(NativePreviewEvent event) {
        // Hide if the element has disappeared (views changed)
        if (shouldHideBubble()) {
            closeBubble();
        }
//        if (!isAttached()) {
//            return;
//        }
        if (isFollowFocus()) {
            if (isFocusMovingEvent(event)) {
                openBubble();
            }
        } else {
            if (isHelpKeyPressed(event)) {
                openBubble();
                event.cancel();
            } else if (shouldHideOnEvent(event)) {
                closeBubble();
            }
        }
    }

    private boolean shouldHideOnEvent(NativePreviewEvent event) {
        Element targetElement = null;
        EventTarget target = event.getNativeEvent().getEventTarget();
        if (Element.is(target)) {
            targetElement = Element.as(target);
        }

        if (closeButton && bubble.isShowing() &&
                event.getTypeInt() == Event.ONMOUSEUP &&
                targetElement != null && bubble.getElement().isOrHasChild(targetElement) &&
                targetElement.hasClassName("v-window-closebox")
        ) {
            return true;
        }

        return hideOnBlur && bubble.isShowing()
                && targetElement != null
                && !bubble.getElement().isOrHasChild(targetElement)
                && isFocusMovingEvent(event)
                && !bubble.helpElement.isOrHasChild(targetElement);
    }

    private boolean shouldHideBubble() {
        if (!hideOnBlur && bubble != null && bubble.helpElement != null) {
            return bubble.helpElement.getAbsoluteLeft() < 0
                    || bubble.helpElement.getAbsoluteTop() < 0
                    || !Document.get().getBody().isOrHasChild(bubble.helpElement)
                    || "hidden".equalsIgnoreCase(bubble.helpElement.getStyle().getVisibility())
                    || "none".equalsIgnoreCase(bubble.helpElement.getStyle().getDisplay());
        }
        return false;
    }

    private void openBubble() {
        restartScrollUpdater();
        setHidden(false);
        fireBubbleMovedEvent(getHelpElement().getId());
    }

    private void restartScrollUpdater() {
        scrollUpdater.cancel();
        scrollUpdater.scheduleRepeating(SCROLL_UPDATER_INTERVAL);
    }

    private void closeBubble() {
        scrollUpdater.cancel();
        setHidden(true);
        bubble.hide();
    }

    public void showHelpBubble(String componentId, String helpText, Placement placement, int horizontalOffset) {
        bubble.showHelpBubble(componentId, helpText, placement, horizontalOffset);
        restartScrollUpdater();
    }

    public void hideHelpBubble() {
        closeBubble();
    }

    private boolean isFocusMovingEvent(NativePreviewEvent event) {
        return isMouseUp(event) || isTabUp(event);
    }

    private boolean isMouseUp(NativePreviewEvent event) {
        return event.getTypeInt() == Event.ONMOUSEUP;
    }

    private boolean isTabUp(NativePreviewEvent event) {
        return (event.getTypeInt() == Event.ONKEYUP
                && event.getNativeEvent().getKeyCode() == KeyCodes.KEY_TAB);
    }

    private boolean isHelpKeyPressed(NativePreviewEvent event) {
        return event.getTypeInt() == Event.ONKEYDOWN && event.getNativeEvent().getKeyCode() == helpKeyCode;
    }

    private boolean isKeyDownOrClick(NativePreviewEvent event) {
        return event.getTypeInt() == Event.ONKEYDOWN || event.getTypeInt() == Event.ONCLICK;
    }

    public native void suppressHelpForIE()
    /*-{
        $doc.onhelp = function () {
            return false;
        }
    }-*/;

    private Element findHelpElement(String id) {
        if (id == null || id.length() == 0) {
            return null;
        }
        Element helpElement = DOM.getElementById(id);
        if (helpElement != null) {
            Element contentElement = findContentElement(helpElement);
            if (contentElement != null) {
                return contentElement;
            }
        }
        return helpElement;
    }

    private Element findContentElement(Element helpElement) {
        // check whether helpElement has a child element with
        // class="v-XXXX-content" and if this is the case, use the content
        // element for the position calculations below.
        NodeList<Node> children = helpElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.getItem(i).getNodeType() == Node.ELEMENT_NODE) {
                Element e = Element.as(children.getItem(i));
                if (e.getClassName().contains("content")) {
                    return e;
                }
            }
        }
        return null;
    }

    public static native Element getFocusedElement()
    /*-{
        return $doc.activeElement;
    }-*/;

    private Element getHelpElement() {
        Element focused = getFocusedElement();
        return findFirstElementInHierarchyWithId(focused);
    }

    private static Element findFirstElementInHierarchyWithId(Element focused) {
        Element elementWithId = focused;
        while (("".equals(elementWithId.getId()) || elementWithId.getId() == null
                || elementWithId.getId().startsWith("gwt-uid"))
                && elementWithId.getParentElement() != null) {
            elementWithId = elementWithId.getParentElement();
        }
        return elementWithId;
    }


    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean isFollowFocus() {
        return followFocus;
    }

    public void setFollowFocus(boolean followFocus) {
        this.followFocus = followFocus;
    }

    public void setCloseButton(boolean value) {
        this.closeButton = value;
    }

    public boolean isCloseButton(boolean value) {
        return this.closeButton;
    }

    public boolean isHideOnBlur() {
        return hideOnBlur;
    }

    public void setHideOnBlur(boolean hideOnBlur) {
        this.hideOnBlur = hideOnBlur;
    }

    public int getHelpKeyCode() {
        return helpKeyCode;
    }

    public void setHelpKeyCode(int helpKeyCode) {
        this.helpKeyCode = helpKeyCode;
    }

    private class HelpBubble extends VOverlay {
        private static final int Z_INDEX_BASE = 19999;

        private final HTML helpHtml;

        private Element helpElement;

        private int elementTop;
        private int elementLeft;

        private Placement placement;
        private int horizontalOffset;

        public HelpBubble() {
            super(false, false); // autoHide, modal
            super.ac = connection;
            setStylePrimaryName(CLASSNAME + "-bubble");
            setZIndex(Z_INDEX_BASE);
            helpHtml = new HTML();
            setWidget(helpHtml);
            // Make sure we are hidden (bypassing event triggering)
            super.hide();
        }

        public void updateStyleNames(String styleNames) {
            StringBuffer styleBuf = new StringBuffer();
            // Copied from ApplicationConnection.updateComponent
            if (styleNames != null && !"".equals(styleNames)) {
                final String[] styles = styleNames.split(" ");
                for (int i = 0; i < styles.length; i++) {
                    styleBuf.append(" ");
                    styleBuf.append(CLASSNAME + "-bubble");
                    styleBuf.append("-");
                    styleBuf.append(styles[i]);
                    styleBuf.append(" ");
                    styleBuf.append(styles[i]);
                }
                addStyleName(styleBuf.toString());
            }
        }

        public void setHelpText(String helpText) {
            helpHtml.setHTML(closeButton ? wrapCloseButton(helpText) : helpText);
            helpHtml.setStyleName("helpText");
        }

        private String wrapCloseButton(String helpText) {
            return "<div style=\"display: flex; align-items: center\">" +
                        "<div style=\"flex-grow:1\">" + helpText + "</div>" +
                        "<div style=\"flex-grow:0; min-width: 25px;min-height:25px\">" +
                            "<div class=\"v-window-closebox\" style=\"border: 1px solid; border-radius: 4px; right: 8px; top: 8px; padding-right: 0; color: white;line-height: 12px;padding-top: 1.5px;height: 16px !important;\"/>" +
                        "</div>" +
                    "</div>";
        }

        public void showHelpBubble(String componentId, String helpText, Placement placement, int horizontalOffset) {
            this.placement = placement;
            this.horizontalOffset = horizontalOffset;
            helpElement = findHelpElement(componentId);
            if (helpElement != null) {
                show();
                setHelpText(helpText);
                calculateAndSetPopupPosition();
            }
            fireBubbleShownEvent(componentId, helpText);
        }

        @Override
        public void hide() {
            super.hide();
            fireBubbleHiddenEvent();
        }

        public void updatePositionIfNeeded() {
            if (isAttached() && !hidden && helpElement != null) {
                if (elementLeft != helpElement.getAbsoluteLeft() || elementTop != helpElement.getAbsoluteTop()) {
                    calculateAndSetPopupPosition();
                }
            }
        }

        private void calculateAndSetPopupPosition() {
            // Save the current position for checking whether the element has moved == scrolled
            elementLeft = helpElement.getAbsoluteLeft();
            elementTop = helpElement.getAbsoluteTop();

            Placement finalPlacement = placement;
            if (placement == Placement.AUTO) {
                finalPlacement = findDefaultPlacement();
            }
            updatePopupStyleForPlacement(finalPlacement);
            setPopupPosition(Math.max(0, getLeft(finalPlacement)), Math.max(0, getTop(finalPlacement)));
        }

        private Placement findDefaultPlacement() {
            // Would the popup go too far to the right?
            if (getLeft(Placement.RIGHT) + getOffsetWidth() > Document.get().getClientWidth()) {
                // Yes, either place it below (if there's room) or above the field
                if (getTop(Placement.BELOW) + getOffsetHeight() < Document.get().getClientHeight()) {
                    return Placement.BELOW;
                } else {
                    return Placement.ABOVE;
                }
            }
            // By default, place the popup to the right of the field
            return Placement.RIGHT;
        }

        private void updatePopupStyleForPlacement(Placement placement) {
            for (Placement p : Placement.values()) {
                removeStyleName(p.name().toLowerCase());
            }
            addStyleName(placement.name().toLowerCase());
        }

        private int getLeft(Placement placement) {
            switch (placement) {
                case RIGHT:
                    return helpElement.getAbsoluteLeft() + helpElement.getOffsetWidth() + horizontalOffset;
                case LEFT:
                    return helpElement.getAbsoluteLeft() - bubble.getOffsetWidth() + horizontalOffset;
                case ABOVE:
                case BELOW:
                    return helpElement.getAbsoluteLeft() + helpElement.getOffsetWidth() / 2 - bubble.getOffsetWidth() / 2 + horizontalOffset;
                case BELOW_LEFT:
                    return helpElement.getAbsoluteLeft() - bubble.getOffsetWidth() / 2 + horizontalOffset;
            }
            return 0;
        }

        public int getTop(Placement placement) {
            switch (placement) {
                case RIGHT:
                case LEFT:
                    return helpElement.getAbsoluteTop() + helpElement.getOffsetHeight() / 2 - bubble.getOffsetHeight() / 2;
                case ABOVE:
                    return helpElement.getAbsoluteTop() - bubble.getOffsetHeight();
                case BELOW:
                    return helpElement.getAbsoluteTop() + helpElement.getOffsetHeight();
            }
            return 0;
        }

        @Override
        public void setPopupPosition(int left, int top) {
            super.setPopupPosition(left, top);
            // Remove the margin styles, that VOverlay forces on the element,
            // in order to be able to move the entire bubble with margins.
            Style style = getElement().getStyle();
            style.clearMarginLeft();
            style.clearMarginTop();
        }
    }
}

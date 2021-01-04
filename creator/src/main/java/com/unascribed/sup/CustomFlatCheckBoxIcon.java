package com.unascribed.sup;

import static com.formdev.flatlaf.FlatClientProperties.SELECTED_STATE;
import static com.formdev.flatlaf.FlatClientProperties.SELECTED_STATE_INDETERMINATE;
import static com.formdev.flatlaf.FlatClientProperties.clientPropertyEquals;

import java.awt.Component;
import java.awt.Graphics2D;

import javax.swing.AbstractButton;
import javax.swing.JComponent;

import com.formdev.flatlaf.icons.FlatCheckBoxIcon;
import com.formdev.flatlaf.ui.FlatButtonUI;
import com.formdev.flatlaf.ui.FlatUIUtils;

public abstract class CustomFlatCheckBoxIcon extends FlatCheckBoxIcon {

	// default behaviors copied from super but busted out of paintIcon
	protected boolean isIndeterminate(Component c) {
		return c instanceof JComponent && clientPropertyEquals( (JComponent) c, SELECTED_STATE, SELECTED_STATE_INDETERMINATE );
	}
	
	protected boolean isSelected(Component c) {
		return (c instanceof AbstractButton && ((AbstractButton)c).isSelected());
	}
	
	protected boolean isFocused(Component c) {
		return FlatUIUtils.isPermanentFocusOwner( c );
	}
	
	// copied from super. only indicated lines are changed
	@Override
	protected void paintIcon( Component c, Graphics2D g2 ) {
		// CHANGES BEGIN
		boolean indeterminate = isIndeterminate(c);
		boolean selected = indeterminate || isSelected(c);
		boolean isFocused = isFocused(c);
		// CHANGES END

		// paint focused border
		if( isFocused && focusWidth > 0 && FlatButtonUI.isFocusPainted( c ) ) {
			g2.setColor( focusColor );
			paintFocusBorder( g2 );
		}

		// paint border
		g2.setColor( FlatButtonUI.buttonStateColor( c,
			selected ? selectedBorderColor : borderColor,
			disabledBorderColor,
			selected && selectedFocusedBorderColor != null ? selectedFocusedBorderColor : focusedBorderColor,
			hoverBorderColor,
			null ) );
		paintBorder( g2 );

		// paint background
		g2.setColor( FlatUIUtils.deriveColor( FlatButtonUI.buttonStateColor( c,
			selected ? selectedBackground : background,
			disabledBackground,
			(selected && selectedFocusedBackground != null) ? selectedFocusedBackground : focusedBackground,
			(selected && selectedHoverBackground != null) ? selectedHoverBackground : hoverBackground,
			(selected && selectedPressedBackground != null) ? selectedPressedBackground : pressedBackground ),
			background ) );
		paintBackground( g2 );

		// paint checkmark
		if( selected || indeterminate ) {
			g2.setColor( c.isEnabled()
				? ((selected && isFocused && selectedFocusedCheckmarkColor != null)
					? selectedFocusedCheckmarkColor
					: checkmarkColor)
				: disabledCheckmarkColor );
			if( indeterminate )
				paintIndeterminate( g2 );
			else
				paintCheckmark( g2 );
		}
	}
	
}

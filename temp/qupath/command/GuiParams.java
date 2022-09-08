/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package ch.epfl.biop.bdv.img.qupath.command;

public class GuiParams {

	private String unit = "MILLIMITER";
	private boolean splitrgbchannels = false;
	private String positioniscenter = "AUTO";
	private String switchzandc = "AUTO";
	private String flippositionx = "AUTO";
	private String flippositiony = "AUTO";
	private String flippositionz = "AUTO";
	private boolean usebioformatscacheblocksize = true;
	private int cachesizex = 512;
	private int cachesizey = 512;
	private int cachesizez = 1;
	private int numberofblockskeptinmemory = -1;
	private double refframesizeinunitlocation = 1.0;
	private double refframesizeinunitvoxsize = 1.0;

	public GuiParams setUnit(String unit) {
		this.unit = unit;
		return this;
	}

	public GuiParams setPositionReferenceFrameLength(
		double frameLocationUnitSize)
	{
		this.refframesizeinunitlocation = frameLocationUnitSize;
		return this;
	}

	public GuiParams setVoxSizeReferenceFrameLength(double frameLocationVoxSize) {
		this.refframesizeinunitvoxsize = frameLocationVoxSize;
		return this;
	}

	public GuiParams setSplitChannels(Boolean splitrgbchannels) {
		this.splitrgbchannels = splitrgbchannels;
		return this;
	}

	public GuiParams setPositioniscenter(String positioniscenter) {
		this.positioniscenter = positioniscenter;
		return this;
	}

	public GuiParams setSwitchzandc(String switchzandc) {
		this.switchzandc = switchzandc;
		return this;
	}

	public GuiParams setFlippositionx(String flippositionx) {
		this.flippositionx = flippositionx;
		return this;
	}

	public GuiParams setFlippositiony(String flippositiony) {
		this.flippositiony = flippositiony;
		return this;
	}

	public GuiParams setFlippositionz(String flippositionz) {
		this.flippositionz = flippositionz;
		return this;
	}

	public GuiParams setCachesizex(int cachesizex) {
		this.cachesizex = cachesizex;
		return this;
	}

	public GuiParams setCachesizey(int cachesizey) {
		this.cachesizey = cachesizey;
		return this;
	}

	public GuiParams setCachesizez(int cachesizez) {
		this.cachesizez = cachesizez;
		return this;
	}

	public GuiParams setNumberofblockskeptinmemory(
		int numberofblockskeptinmemory)
	{
		this.numberofblockskeptinmemory = numberofblockskeptinmemory;
		return this;
	}

	public GuiParams setUsebioformatscacheblocksize(
		Boolean usebioformatscacheblocksize)
	{
		this.usebioformatscacheblocksize = usebioformatscacheblocksize;
		return this;
	}

	public Boolean getUsebioformatscacheblocksize() {
		return this.usebioformatscacheblocksize;
	}

	public int getNumberofblockskeptinmemory() {
		return this.numberofblockskeptinmemory;
	}

	public int getCachesizez() {
		return this.cachesizez;
	}

	public int getCachesizey() {
		return this.cachesizey;
	}

	public int getCachesizex() {
		return this.cachesizex;
	}

	public String getPositoniscenter() {
		return this.positioniscenter;
	}

	public String getFlippositionz() {
		return this.flippositionz;
	}

	public String getFlippositiony() {
		return this.flippositiony;
	}

	public String getFlippositionx() {
		return this.flippositionx;
	}

	public String getSwitchzandc() {
		return this.switchzandc;
	}

	public Boolean getSplitChannels() {
		return this.splitrgbchannels;
	}

	public double getVoxSizeReferenceFrameLength() {
		return this.refframesizeinunitvoxsize;
	}

	public double getRefframesizeinunitlocation() {
		return this.refframesizeinunitlocation;
	}

	public String getUnit() {
		return this.unit;
	}

	public GuiParams() {}

}

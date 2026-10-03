/*
 * Copyright 2026; Réal Demers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rd.fullstack.springbooteda.util.token;

public class ShuffleBits {
	public final static int USHORT_MIN_VALUE = 0;
	public final static int USHORT_MAX_VALUE = 0xffff;

	public static final boolean CST_FWD = true;
	public static final boolean CST_RWD = false;
	
	public static final int CST_SEED_A = 0;
	public static final int CST_SEED_B = 1;
	public static final int CST_SEED_RATIO = 2;
	
	public static final long SEED_RATIO = 0x9e3779b9L;
	
	private boolean mode;
	private long seedA, seedB;
	private int value;
	
	public ShuffleBits() {
		super();
		init();
	}

	public void init() {
		init(CST_FWD, SEED_RATIO, SEED_RATIO);
	}
	
	public void init(boolean mode, long seedA, long seedB) {
		this.mode  = mode;
		this.seedA = seedA;
		this.seedB = seedB;
		this.value = 0;
	}		
	
	public void update(int value) {

		this.value = value;
		if (mode) {
			mix32Fwd();
		} else {
			mix32Rwd();
		}
	}
	
	public int finale() {
		return value;
	}
	
	public long getSeed(int seed) {
		switch (seed) {
			case CST_SEED_A:
				return seedA;
			case CST_SEED_B:
				return seedB;
			case CST_SEED_RATIO:
				return SEED_RATIO;
		}
		return 0;
	}
	
	private void mix32Fwd() {

		int high, low;
		high  = getUnShort((int)value>>16);
		low   = getUnShort((int)value);
		
		high  = getUnShort(high+getUnShort(low<<6)); low  = getUnShort(low^getUnShort(high>>11));
		high  = getUnShort(high+getUnShort(low<<2)); low  = getUnShort(low^getUnShort(high>>4));
		low   = getUnShort(low+getUnShort(high<<5)); high = getUnShort(high^getUnShort(low>>1));
		low   = getUnShort(low+getUnShort(high<<3)); high = getUnShort(high^getUnShort(low>>6));
		
		value = high;          
		value = value<<16; 
		value += low;

		// Utilisee par seedA & seedB.
		int lowValue  = low;
		int highValue = high;

		// seedA - (DEPENDS value high/low).
		high  = getUnShort(getUnShort((int)seedA>>16)^highValue);
		low   = getUnShort(getUnShort((int)seedA)^lowValue);
		
		high  = getUnShort(high+getUnShort(low<<6)); low  = getUnShort(low^getUnShort(high>>11));
		high  = getUnShort(high+getUnShort(low<<2)); low  = getUnShort(low^getUnShort(high>>4));
		low   = getUnShort(low+getUnShort(high<<5)); high = getUnShort(high^getUnShort(low>>1));
		low   = getUnShort(low+getUnShort(high<<3)); high = getUnShort(high^getUnShort(low>>6));
		
		seedA = high;          
		seedA = seedA<<16; 
		seedA += low;

		high  = getUnShort(getUnShort((int)seedB>>16)^lowValue);
		low   = getUnShort(getUnShort((int)seedB)^highValue);
		high  = getUnShort(high+getUnShort(low<<6)); low  = getUnShort(low^getUnShort(high>>11));
		high  = getUnShort(high+getUnShort(low<<2)); low  = getUnShort(low^getUnShort(high>>4));
		low   = getUnShort(low+getUnShort(high<<5)); high = getUnShort(high^getUnShort(low>>1));
		low   = getUnShort(low+getUnShort(high<<3)); high = getUnShort(high^getUnShort(low>>6));
		
		seedB = high;          
		seedB = seedB<<16; 
		seedB += low;
	}

	private void mix32Rwd() {
		int high, low;
		high = getUnShort((int)value>>16);
		low  = getUnShort((int)value);

		int lowValue  = low;
		int highValue = high;

		high  = getUnShort(high^getUnShort(low>>6));  low  = getUnShort(low-getUnShort(high<<3));
		high  = getUnShort(high^getUnShort(low>>1));  low  = getUnShort(low-getUnShort(high<<5));
		low   = getUnShort(low^getUnShort(high>>4));  high = getUnShort(high-getUnShort(low<<2));
		low   = getUnShort(low^getUnShort(high>>11)); high = getUnShort(high-getUnShort(low<<6));
		
		value = high;              
		value = value<<16; 
		value += low;

		high  = getUnShort((int)seedA>>16);
		low   = getUnShort((int)seedA);
		high  = getUnShort(high^getUnShort(low>>6));  low  = getUnShort(low-getUnShort(high<<3));
		high  = getUnShort(high^getUnShort(low>>1));  low  = getUnShort(low-getUnShort(high<<5));
		low   = getUnShort(low^getUnShort(high>>4));  high = getUnShort(high-getUnShort(low<<2));
		low   = getUnShort(low^getUnShort(high>>11)); high = getUnShort(high-getUnShort(low<<6));
		
		seedA = getUnShort(high^highValue); 
		seedA = seedA<<16; 
		seedA += getUnShort(low^lowValue);

		high  = getUnShort((int)seedB>>16);
		low   = getUnShort((int)seedB);
		high  = getUnShort(high^getUnShort(low>>6));  low  = getUnShort(low-getUnShort(high<<3));
		high  = getUnShort(high^getUnShort(low>>1));  low  = getUnShort(low-getUnShort(high<<5));
		low   = getUnShort(low^getUnShort(high>>4));  high = getUnShort(high-getUnShort(low<<2));
		low   = getUnShort(low^getUnShort(high>>11)); high = getUnShort(high-getUnShort(low<<6));
		
		seedB = getUnShort(high^lowValue);              
		seedB = seedB<<16; 
		seedB += getUnShort(low^highValue);
	}
	
	private static int getUnShort(int value) {
		return (value & USHORT_MAX_VALUE);
	}
}
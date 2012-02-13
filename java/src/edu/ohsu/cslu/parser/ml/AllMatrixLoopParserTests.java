/*
 * Copyright 2010, 2011 Aaron Dunlop and Nathan Bodenstab
 * 
 * This file is part of the BUBS Parser.
 * 
 * The BUBS Parser is free software: you can redistribute it and/or 
 * modify  it under the terms of the GNU Affero General Public License 
 * as published by the Free Software Foundation, either version 3 of 
 * the License, or (at your option) any later version.
 * 
 * The BUBS Parser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with the BUBS Parser. If not, see <http://www.gnu.org/licenses/>
 */
package edu.ohsu.cslu.parser.ml;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Unit test suite for all matrix-loop parsers
 * 
 * @author Aaron Dunlop
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({ TestLeftChildLoopSpmlParser.class, TestRightChildLoopSpmlParser.class,
        TestCartesianProductBinarySearchSpmlParser.class, TestCartesianProductBinarySearchLeftChildSpmlParser.class,
        TestCartesianProductHashSpmlParser.class, TestGrammarLoopSpmlParser.class,
        TestPrunedCartesianProductHashSpmlParser.class, TestInsideOutsideCphSpmlParser.class,
        TestConstrainedCphSpmlParser.class })
public class AllMatrixLoopParserTests {
}

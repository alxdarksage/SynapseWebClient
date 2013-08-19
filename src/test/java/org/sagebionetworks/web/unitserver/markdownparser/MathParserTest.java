package org.sagebionetworks.web.unitserver.markdownparser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.web.server.ServerMarkdownUtils;
import org.sagebionetworks.web.server.markdownparser.MarkdownElements;
import org.sagebionetworks.web.server.markdownparser.MathParser;

public class MathParserTest {
	MathParser parser;
	String testEquation = "\\begin{aligned}\n" +
			"\\nabla \\times \\vec{\\mathbf{B}} -\\, \\frac1c\\, \\frac{\\partial\\vec{\\mathbf{E}}}{\\partial t} & = \\frac{4\\pi}{c}\\vec{\\mathbf{j}} \\\\   \\nabla \\cdot \\vec{\\mathbf{E}} & = 4 \\pi \\rho \\\\\n" +
			"\\nabla \\times \\vec{\\mathbf{E}}\\, +\\, \\frac1c\\, \\frac{\\partial\\vec{\\mathbf{B}}}{\\partial t} & = \\vec{\\mathbf{0}} \\\\\n" +
			"\\nabla \\cdot \\vec{\\mathbf{B}} & = 0 \\end{aligned}";
	
	@Before
	public void setup(){
		parser = new MathParser();
	}
	
	@Test
	public void testMathBlock(){
		String line = "$$";
		MarkdownElements elements = new MarkdownElements(line);
		parser.processLine(elements);
		String result = elements.getHtml();
		assertTrue(result.contains(ServerMarkdownUtils.START_MATH));
		assertFalse(result.contains(ServerMarkdownUtils.END_MATH));
		
		assertTrue(parser.isInMarkdownElement());
		
		//feed test equation
		for (String l : testEquation.split("\n")) {
			elements = new MarkdownElements(l);
			parser.processLine(elements);
			result = elements.getHtml();
			assertFalse(result.contains(ServerMarkdownUtils.START_MATH));
			assertTrue(result.contains(l));
			assertFalse(result.contains(ServerMarkdownUtils.END_MATH));
			assertTrue(parser.isInMarkdownElement());
		}
		
		//last line
		line =  "$$";
		elements = new MarkdownElements(line);
		parser.processLine(elements);
		result = elements.getHtml();
		assertFalse(result.contains(ServerMarkdownUtils.START_MATH));
		assertTrue(result.contains(ServerMarkdownUtils.END_MATH));
		assertFalse(parser.isInMarkdownElement());
	}

}

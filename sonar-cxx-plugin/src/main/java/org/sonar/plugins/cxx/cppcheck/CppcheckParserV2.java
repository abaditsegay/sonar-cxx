/*
 * Sonar C++ Plugin (Community)
 * Copyright (C) 2010 Neticoa SAS France
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.cxx.cppcheck;

import java.io.File;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.lang.StringUtils;
import org.codehaus.staxmate.in.SMHierarchicCursor;
import org.codehaus.staxmate.in.SMInputCursor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.resources.Project;
import org.sonar.api.utils.StaxParser;
import org.sonar.plugins.cxx.utils.CxxUtils;
import org.sonar.plugins.cxx.utils.EmptyReportException;

/**
 * {@inheritDoc}
 */
public class CppcheckParserV2 implements CppcheckParser {

  private CxxCppCheckSensor sensor;

  public CppcheckParserV2(CxxCppCheckSensor sensor) {
    this.sensor = sensor;
  }

  /**
   * {@inheritDoc}
   */
  public void processReport(final Project project, final SensorContext context, File report)
    throws javax.xml.stream.XMLStreamException {
    CxxUtils.LOG.info("Parsing 'Cppcheck V2' format");

    StaxParser parser = new StaxParser(new StaxParser.XmlStreamHandler() {
      /**
       * {@inheritDoc}
       */
      public void stream(SMHierarchicCursor rootCursor) throws XMLStreamException {
        boolean parsed = false;

        try {
          rootCursor.advance();
        } catch (com.ctc.wstx.exc.WstxEOFException eofExc) {
          throw new EmptyReportException();
        }

        try {
          String version = rootCursor.getAttrValue("version");
          if ("2".equals(version)) {
            SMInputCursor errorsCursor = rootCursor.childElementCursor("errors");
            if (errorsCursor.getNext() != null) {
              parsed = true;
              SMInputCursor errorCursor = errorsCursor.childElementCursor("error");
              while (errorCursor.getNext() != null) {
                String id = errorCursor.getAttrValue("id");
                String msg = createMsg(
                  errorCursor.getAttrValue("inconclusive"),
                  errorCursor.getAttrValue("msg")
                );
                String file = null;
                String line = null;

                SMInputCursor locationCursor = errorCursor.childElementCursor("location");
                if (locationCursor.getNext() != null) {
                  file = locationCursor.getAttrValue("file");
                  line = locationCursor.getAttrValue("line");

                  if ("*".equals(file)) { // findings on project level
                    file = null;
                    line = null;
                  }
                }

                if (isInputValid(file, line, id, msg)) {
                  sensor.saveUniqueViolation(project, context, CxxCppCheckRuleRepository.KEY, file, line, id, msg);
                } else {
                  CxxUtils.LOG.warn("Skipping invalid violation: '{}'", msg);
                }
              }
            }
          }
        } catch (RuntimeException e) {
          throw new XMLStreamException();
        }

        if (!parsed) {
          throw new XMLStreamException();
        }
      }

      private String createMsg(String inconclusive, String msg) {
        if (!StringUtils.isEmpty(msg)) {
          if (!StringUtils.isEmpty(inconclusive) && "true".equals(inconclusive)) {
            return "[inconclusive] " + msg;
          }
        }
        return msg;
      }

      private boolean isInputValid(String file, String line, String id, String msg) {
        return !StringUtils.isEmpty(id) && !StringUtils.isEmpty(msg);
      }
    });

    parser.parse(report);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}

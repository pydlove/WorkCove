package com.pydlove.workcove.development.domain.model;

import com.pydlove.workcove.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZentaoBaseUrlTest {

    @Test
    void normalizesPastedBugPageUrlToContextPath() {
        assertEquals(
                "http://chandao.api.com/zentao",
                ZentaoBaseUrl.normalize(
                        "http://chandao.api.com/zentao/project-bug-1448-0-all-assignedTo_asc"));
    }

    @Test
    void removesApiSuffixAndQueryParameters() {
        assertEquals(
                "https://zentao.example.com/zentao",
                ZentaoBaseUrl.normalize(
                        "https://zentao.example.com/zentao/api.php/v2/products?pageID=1"));
    }

    @Test
    void preservesOriginWhenNoContextPathExists() {
        assertEquals(
                "https://zentao.example.com",
                ZentaoBaseUrl.normalize("https://zentao.example.com/"));
    }

    @Test
    void rejectsNonHttpAddress() {
        assertThrows(
                DomainException.class,
                () -> ZentaoBaseUrl.normalize("ftp://zentao.example.com"));
    }

    @Test
    void resolvesMediaOnlyInsideTheConfiguredZentaoOrigin() {
        assertEquals(
                "https://zentao.example.com/zentao/file-read-1.png",
                ZentaoBaseUrl.resolveAsset(
                        "https://zentao.example.com/zentao",
                        "file-read-1.png").toString());
        assertEquals(
                "https://zentao.example.com/zentao/file-read-102287.png",
                ZentaoBaseUrl.resolveAsset(
                        "https://zentao.example.com/zentao",
                        "{102287.png}").toString());
        assertThrows(
                DomainException.class,
                () -> ZentaoBaseUrl.resolveAsset(
                        "https://zentao.example.com/zentao",
                        "https://attacker.example.com/image.png"));
    }
}

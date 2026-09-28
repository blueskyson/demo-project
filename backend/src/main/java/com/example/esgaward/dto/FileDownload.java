package com.example.esgaward.dto;

import org.springframework.core.io.Resource;

/** A proposal file's metadata together with its content, for streaming a download. */
public record FileDownload(ProposalFileDto file, Resource content) {
}

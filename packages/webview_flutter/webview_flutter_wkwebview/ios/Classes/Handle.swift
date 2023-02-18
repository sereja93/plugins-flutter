//
//  RTest.swift
//  Runner
//
//  Created by Ivanov Sereja on 18.02.2023.
//  Copyright © 2023 The Flutter Authors. All rights reserved.
//

//
//  Handle.swift
//  webview_flutter_wkwebview
//
//  Created by Ivanov Sereja on 18.02.2023.
//
//


import Foundation

@objc public class AsyncCallback: NSObject {
    let challenge: URLAuthenticationChallenge
    let completion: ((URLSession.AuthChallengeDisposition, URLCredential?) -> Void)
    
    @objc public init(
        challenge: URLAuthenticationChallenge,
        completion: @escaping ((URLSession.AuthChallengeDisposition, URLCredential?) -> Void)
    ) {
        self.challenge = challenge
        self.completion = completion
    }
}

@objc public class AsyncAuthChallengeHandler: NSObject {
    @objc public var handle: (AsyncCallback) -> Void
    
    
    init(handler: @escaping (AsyncCallback) -> Void) {
        handle = handler
    }
    
    static func handle(_ handler: @escaping (AsyncCallback) -> Void) -> AsyncAuthChallengeHandler { .init(handler: handler) }
    
    
    private static let handlerQueue = DispatchQueue(label: "WKWebView.AuthChallengeHandler")
    
    
   @objc public static func webViewAddTrusted(certificates: [Data],
                                  ignoreUserCertificates: Bool = true) -> AsyncAuthChallengeHandler {
        
        .handle { callback  in
            let challenge = callback.challenge
            let completion = callback.completion
            handlerQueue.async {
                guard ignoreUserCertificates else {
                    let result = AuthChallengeHandler.chain(
                        .setAnchor(certificates: certificates, includeSystemAnchors: true),
                        .secTrustEvaluateSSL(withCustomCerts: true)
                    ).handle(challenge)
                    completion(result.0, result.1)
                    return
                }
                
                // Пробуем валидацию без доп. сертификатов
                _ = AuthChallengeHandler.setAnchor(
                    certificates: [], includeSystemAnchors: true
                ).handle(challenge)
                
                // Проводим валидацию цепочки
                let systemCertsResult = AuthChallengeHandler.secTrustEvaluateSSL(
                    // Сертификаты пользователя игнориуем
                    withCustomCerts: false
                ).handle(challenge)
                
                guard
                    // Если это не NSURLAuthenticationMethodServerTrust
                    systemCertsResult.0 != .performDefaultHandling,
                    // Если цепочка прошла валидацию и добавление сертификата не понадобилось
                    systemCertsResult.0 != .useCredential
                else {
                    completion(systemCertsResult.0, systemCertsResult.1)
                    return
                }
                
                // Добавляем наши сертификаты
                _ = AuthChallengeHandler.setAnchor(
                    certificates: certificates,
                    // Игнорируем сертификаты пользователя и системные
                    includeSystemAnchors: false
                ).handle(challenge)
                
                // Если валидация не прошла на этом этапе, то цепочка невалидна, либо Root недоверенный
                let customCertsResult = AuthChallengeHandler
                    .secTrustEvaluateSSL(withCustomCerts: true)
                    .handle(challenge)
                
                completion(customCertsResult.0, customCertsResult.1)
            }
        }
    }
}


struct AuthChallengeHandler {
    
    static func secTrustEvaluateSSL(withCustomCerts: Bool) -> AuthChallengeHandler {
        .handle {
            guard let trust = serverTrust($0) else {
                return (.performDefaultHandling, nil)
            }
            guard evaluate(trust, host: $0.protectionSpace.host, allowCustomRootCertificate: withCustomCerts) else {
                return (.cancelAuthenticationChallenge, nil)
            }
            return (.useCredential, URLCredential(trust: trust))
        }
    }
    
    static func serverTrust(_ authChallenge: URLAuthenticationChallenge) -> SecTrust? {
        guard authChallenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust else { return nil }
        return authChallenge.protectionSpace.serverTrust
    }
    
    static func evaluate(_ trust: SecTrust, host: String, allowCustomRootCertificate: Bool) -> Bool {
        let sslPolicy = SecPolicyCreateSSL(true, host as CFString)
        let status = SecTrustSetPolicies(trust, sslPolicy)
        if status != errSecSuccess {
            return false
        }
        
        var error: CFError?
        if #available(iOS 12.0, *) {
            guard SecTrustEvaluateWithError(trust, &error) && error == nil else {
                return false
            }
        } else {
            return false
        }
        var result = SecTrustResultType.invalid
        let getTrustStatus = SecTrustGetTrustResult(trust, &result)
        guard getTrustStatus == errSecSuccess && (result == .unspecified || result == .proceed) else {
            return false
        }
        if allowCustomRootCertificate == false && result == .proceed { return false }
        return true
    }
    
    var handle: (URLAuthenticationChallenge) -> (URLSession.AuthChallengeDisposition, URLCredential?)
    
    
    init(handler: @escaping (URLAuthenticationChallenge) -> (URLSession.AuthChallengeDisposition, URLCredential?)) {
        handle = handler
    }
    
    static func handle(_ handler: @escaping ((URLAuthenticationChallenge) -> (URLSession.AuthChallengeDisposition, URLCredential?))) -> AuthChallengeHandler { .init(handler: handler) }
    
    
    static func chain(first: AuthChallengeHandler, other handlers: [AuthChallengeHandler],
                      passOverWhen passOver: @escaping ((URLSession.AuthChallengeDisposition, URLCredential?)) -> Bool) -> AuthChallengeHandler {
        .handle {
            let firstHandlerResult = first.handle($0)
            guard passOver(firstHandlerResult) else { return firstHandlerResult }

            for handler in handlers {
                let handlingResult = handler.handle($0)
                guard passOver(handlingResult) else {
                    return handlingResult
                }
            }

            return firstHandlerResult
        }
    }
    
    static func chain(_ nonEmpty: AuthChallengeHandler, _ handlers: AuthChallengeHandler...) -> AuthChallengeHandler {
        chain(first: nonEmpty, other: handlers, passOverWhen: { $0.0 == .performDefaultHandling })
    }
    
    
    static func setAnchor(certificates: [Data], includeSystemAnchors: Bool = false) -> Self {
        return .handle {
            guard let trust = serverTrust($0) else {
                return (.performDefaultHandling, nil)
            }
            SecTrustSetAnchorCertificates(
                trust, certificates.compactMap { SecCertificateCreateWithData(nil, $0 as CFData) } as CFArray
            )
            SecTrustSetAnchorCertificatesOnly(trust, !includeSystemAnchors)
            return (.performDefaultHandling, nil)
        }
    }
}


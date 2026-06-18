declare const Api: {
    pluginMethod_GetMacros(): string;
    pluginMethod_SetMacros(json: string): void;
    [method: string]: (...args: any[]) => any;
};

declare namespace Asc {
    const plugin: {
        init: (() => void) | undefined;
        button: ((id: number) => void) | undefined;
        callCommand(
            cmd: () => any,
            isCalcOn: boolean,
            isShowError: boolean,
            callback: (result: string) => void
        ): void;
        executeCommand(type: string, data: string): void;
    };
    const scope: Record<string, any>;
}